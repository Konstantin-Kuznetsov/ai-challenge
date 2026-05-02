package week04.task1.service

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import week04.task1.config.AppEnv
import week04.task1.model.TokenUsage
import kotlin.system.measureTimeMillis

data class DecisionResult(
    val decision: ToolDecision,
    val usage: TokenUsage? = null,
    val latencyMs: Long = 0,
    val provider: String,
    val raw: String? = null,
)

class LlmDecisionService(
    private val httpClient: HttpClient = HttpClient {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    },
) {
    suspend fun decide(message: String): DecisionResult {
        if (!AppEnv.hasYandexCredentials()) {
            val fallback = decideTool(message)
            return DecisionResult(
                decision = fallback.copy(
                    reason = "LLM router fallback: нет YANDEX_API_KEY/YANDEX_FOLDER_ID. ${fallback.reason}",
                ),
                latencyMs = 0,
                provider = "fallback-heuristic",
            )
        }

        val apiKey = AppEnv.get("YANDEX_API_KEY") ?: return DecisionResult(decideTool(message), provider = "fallback-heuristic")
        val folderId = AppEnv.get("YANDEX_FOLDER_ID") ?: return DecisionResult(decideTool(message), provider = "fallback-heuristic")
        val model = AppEnv.get("YANDEX_MODEL", "yandexgpt-lite")

        val routingPrompt = """
            Ты роутер инструментов MCP. Ответь ТОЛЬКО JSON без markdown.
            Доступные инструменты:
            - getCoinPrice(symbol, vsCurrency)
            - getTrendingCoins()
            - getMarketSnapshot(vsCurrency, limit)
            - getCoinOhlc(symbol, vsCurrency, days)

            Верни JSON-схему:
            {
              "useMcp": true|false,
              "tool": "getCoinPrice|getTrendingCoins|getMarketSnapshot|getCoinOhlc|null",
              "args": {"symbol":"BTC","vsCurrency":"usd","days":"1","limit":5},
              "reason": "краткое объяснение по-русски"
            }

            Правила:
            - Если запрос не про актуальные крипто-данные/курс/рынок, useMcp=false и tool=null.
            - symbol по умолчанию BTC, vsCurrency по умолчанию usd.
            - limit от 1 до 20.
            - reason короткий и понятный.
        """.trimIndent()

        val requestBody = buildJsonObject {
            put("modelUri", "gpt://$folderId/$model")
            putJsonObject("completionOptions") {
                put("stream", false)
                put("temperature", 0.0)
                put("maxTokens", "220")
            }
            put("messages", buildJsonArray {
                add(buildJsonObject {
                    put("role", "system")
                    put("text", routingPrompt)
                })
                add(buildJsonObject {
                    put("role", "user")
                    put("text", message)
                })
            })
        }

        lateinit var payload: JsonObject
        val latencyMs = measureTimeMillis {
            payload = httpClient.post("https://llm.api.cloud.yandex.net/foundationModels/v1/completion") {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Api-Key $apiKey")
                setBody(requestBody)
            }.body()
        }

        val alternative = payload["result"]
            ?.jsonObject
            ?.get("alternatives")
            ?.jsonArray
            ?.firstOrNull()
            ?.jsonObject

        val text = alternative
            ?.get("message")
            ?.jsonObject
            ?.get("text")
            ?.jsonPrimitive
            ?.content
            ?.trim()
            .orEmpty()

        val usageObj = payload["result"]?.jsonObject?.get("usage")?.jsonObject
        val usage = TokenUsage(
            inputTokens = usageObj?.get("inputTextTokens")?.jsonPrimitive?.intOrNull ?: 0,
            outputTokens = usageObj?.get("completionTokens")?.jsonPrimitive?.intOrNull ?: 0,
            totalTokens = (usageObj?.get("inputTextTokens")?.jsonPrimitive?.intOrNull ?: 0) +
                (usageObj?.get("completionTokens")?.jsonPrimitive?.intOrNull ?: 0),
        )

        val parsedDecision = parseDecisionJson(text) ?: decideTool(message).copy(
            reason = "LLM вернул невалидный JSON, fallback на rule-based. $text",
        )

        return DecisionResult(
            decision = parsedDecision,
            usage = usage,
            latencyMs = latencyMs,
            provider = "yandex:$model",
            raw = text,
        )
    }

    private fun parseDecisionJson(text: String): ToolDecision? {
        val normalized = text
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        if (normalized.isBlank()) return null

        return runCatching {
            val root = Json.parseToJsonElement(normalized).jsonObject
            val useMcp = root["useMcp"]?.jsonPrimitive?.booleanOrNull ?: false
            val toolRaw = root["tool"]?.jsonPrimitive?.contentOrNull
            val tool = toolRaw?.takeIf { it != "null" }
            val reason = root["reason"]?.jsonPrimitive?.contentOrNull ?: "LLM decision."
            val args = root["args"]?.jsonObject ?: JsonObject(emptyMap())

            val normalizedArgs = mutableMapOf<String, JsonPrimitive>()
            args["symbol"]?.jsonPrimitive?.contentOrNull?.let { normalizedArgs["symbol"] = JsonPrimitive(it.uppercase()) }
            args["vsCurrency"]?.jsonPrimitive?.contentOrNull?.let { normalizedArgs["vsCurrency"] = JsonPrimitive(it.lowercase()) }
            args["days"]?.jsonPrimitive?.contentOrNull?.let { normalizedArgs["days"] = JsonPrimitive(it) }
            args["limit"]?.jsonPrimitive?.intOrNull?.coerceIn(1, 20)?.let { normalizedArgs["limit"] = JsonPrimitive(it) }

            ToolDecision(
                shouldUseMcp = useMcp && tool != null,
                name = if (useMcp) tool else null,
                arguments = normalizedArgs,
                reason = reason,
            )
        }.getOrNull()
    }
}
