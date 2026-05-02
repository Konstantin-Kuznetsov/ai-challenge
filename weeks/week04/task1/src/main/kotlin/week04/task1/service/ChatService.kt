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
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import week04.task1.config.AppEnv
import week04.task1.config.JsonConfig
import week04.task1.model.NormalizedToolResult
import week04.task1.model.QuoteCard
import week04.task1.model.SchemaValidationResult
import week04.task1.model.TokenUsage
import week04.task1.model.TraceStep

data class ToolDecision(
    val shouldUseMcp: Boolean,
    val name: String?,
    val arguments: Map<String, JsonElement>,
    val reason: String,
)

data class LlmSynthesis(
    val answer: String,
    val usage: TokenUsage,
    val traceStep: TraceStep,
)

private val synthesisHttpClient: HttpClient by lazy {
    HttpClient {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }
}

fun decideTool(message: String): ToolDecision {
    val lowered = message.lowercase()
    val looksLikeCryptoQuery = listOf(
        "btc", "eth", "sol", "xrp", "крипт", "биткоин", "эфир", "курс", "цена",
        "сколько стоит", "рынок", "капитализац", "тренд", "волатиль", "ohlc", "график",
    ).any { it in lowered }

    if (!looksLikeCryptoQuery) {
        return ToolDecision(
            shouldUseMcp = false,
            name = null,
            arguments = emptyMap(),
            reason = "Запрос не требует рыночных данных, отвечаю без MCP-инструмента.",
        )
    }

    return when {
        "trend" in lowered || "trending" in lowered || "тренд" in lowered -> ToolDecision(
            shouldUseMcp = true,
            name = "getTrendingCoins",
            arguments = emptyMap(),
            reason = "Запрошен тренд, использую getTrendingCoins.",
        )
        "snapshot" in lowered || "market" in lowered || "рынок" in lowered || "капитализац" in lowered -> ToolDecision(
            shouldUseMcp = true,
            name = "getMarketSnapshot",
            mapOf("vsCurrency" to JsonPrimitive("usd"), "limit" to JsonPrimitive(5)),
            reason = "Запрошен обзор рынка, использую getMarketSnapshot.",
        )
        "ohlc" in lowered || "chart" in lowered || "график" in lowered -> ToolDecision(
            shouldUseMcp = true,
            name = "getCoinOhlc",
            mapOf("symbol" to JsonPrimitive("BTC"), "vsCurrency" to JsonPrimitive("usd"), "days" to JsonPrimitive("1")),
            reason = "Запрошен график/свечи, использую getCoinOhlc.",
        )
        else -> ToolDecision(
            shouldUseMcp = true,
            name = "getCoinPrice",
            mapOf("symbol" to JsonPrimitive("BTC"), "vsCurrency" to JsonPrimitive("usd")),
            reason = "Запрошен курс/цена, использую getCoinPrice.",
        )
    }
}

suspend fun synthesizeAnswer(message: String, decision: ToolDecision, normalizedResult: NormalizedToolResult?): LlmSynthesis {
    if (AppEnv.hasYandexCredentials()) {
        llmSynthesisAnswer(message, decision, normalizedResult)?.let { return it }
    }

    val fallbackAnswer = fallbackSynthesisAnswer(message, decision, normalizedResult)
    val inputTokens = estimateTokens(message) + estimateTokens(normalizedResult?.summary ?: "")
    val outputTokens = estimateTokens(fallbackAnswer)
    return LlmSynthesis(
        answer = fallbackAnswer,
        usage = TokenUsage(
            inputTokens = inputTokens,
            outputTokens = outputTokens,
            totalTokens = inputTokens + outputTokens,
        ),
        traceStep = TraceStep(name = "llm.synthesis", latencyMs = 0, status = "ok:fallback"),
    )
}

private suspend fun llmSynthesisAnswer(
    message: String,
    decision: ToolDecision,
    normalizedResult: NormalizedToolResult?,
): LlmSynthesis? {
    val apiKey = AppEnv.get("YANDEX_API_KEY") ?: return null
    val folderId = AppEnv.get("YANDEX_FOLDER_ID") ?: return null
    val model = AppEnv.get("YANDEX_MODEL", "yandexgpt-lite")

    val systemPrompt = """
        Ты ассистент в демо MCP.
        Отвечай по-русски очень кратко и по делу (1-3 предложения).
        Если у тебя есть tool summary, используй его как источник фактов.
        Не пиши служебные пояснения про MCP, если это не просили явно.
    """.trimIndent()

    val toolContext = normalizedResult?.summary?.let { "Tool summary: $it" }

    val body = buildJsonObject {
        put("modelUri", "gpt://$folderId/$model")
        putJsonObject("completionOptions") {
            put("stream", false)
            put("temperature", 0.2)
            put("maxTokens", "220")
        }
        put("messages", buildJsonArray {
            add(buildJsonObject {
                put("role", "system")
                put("text", systemPrompt)
            })
            add(buildJsonObject {
                put("role", "user")
                put(
                    "text",
                    if (decision.shouldUseMcp && toolContext != null) {
                        "Вопрос пользователя: $message\n$toolContext"
                    } else {
                        "Вопрос пользователя: $message"
                    },
                )
            })
        })
    }

    val started = System.nanoTime()
    val payload = synthesisHttpClient.post("https://llm.api.cloud.yandex.net/foundationModels/v1/completion") {
        contentType(ContentType.Application.Json)
        header(HttpHeaders.Authorization, "Api-Key $apiKey")
        setBody(body)
    }.body<JsonObject>()
    val elapsedMs = (System.nanoTime() - started) / 1_000_000

    val answer = payload["result"]
        ?.jsonObject
        ?.get("alternatives")
        ?.jsonArray
        ?.firstOrNull()
        ?.jsonObject
        ?.get("message")
        ?.jsonObject
        ?.get("text")
        ?.jsonPrimitive
        ?.content
        ?.trim()
        .orEmpty()
    if (answer.isBlank()) return null

    val usageObj = payload["result"]?.jsonObject?.get("usage")?.jsonObject
    val inputTokens = usageObj?.get("inputTextTokens")?.jsonPrimitive?.intOrNull ?: estimateTokens(message)
    val outputTokens = usageObj?.get("completionTokens")?.jsonPrimitive?.intOrNull ?: estimateTokens(answer)

    return LlmSynthesis(
        answer = answer,
        usage = TokenUsage(
            inputTokens = inputTokens,
            outputTokens = outputTokens,
            totalTokens = inputTokens + outputTokens,
        ),
        traceStep = TraceStep(name = "llm.synthesis", latencyMs = elapsedMs, status = "ok:yandex:$model"),
    )
}

private fun fallbackSynthesisAnswer(
    message: String,
    decision: ToolDecision,
    normalizedResult: NormalizedToolResult?,
): String {
    if (decision.shouldUseMcp && normalizedResult != null) {
        return normalizedResult.summary
    }

    val q = message.lowercase()
    return when {
        "расстояние до луны" in q -> "Среднее расстояние до Луны около 384 400 км."
        "кто ты" in q -> "Я чат-ассистент с поддержкой MCP: могу отвечать и при необходимости подключать инструменты."
        else -> "Понял. Кратко: $message"
    }
}

private fun estimateTokens(text: String): Int = text.split(Regex("\\s+")).filter { it.isNotBlank() }.size

fun validateToolArguments(toolName: String?, args: Map<String, JsonElement>): SchemaValidationResult {
    if (toolName == null) return SchemaValidationResult(valid = true)
    val errors = mutableListOf<String>()

    fun hasString(key: String) = args[key]?.jsonPrimitive?.contentOrNull?.isNotBlank() == true
    fun hasInt(key: String) = args[key]?.jsonPrimitive?.intOrNull != null

    when (toolName) {
        "getCoinPrice" -> {
            if (!hasString("symbol")) errors += "symbol is required"
        }
        "getTrendingCoins" -> {}
        "getMarketSnapshot" -> {
            if ("limit" in args && !hasInt("limit")) errors += "limit must be integer"
        }
        "getCoinOhlc" -> {
            if (!hasString("symbol")) errors += "symbol is required"
            if ("days" in args && args["days"]?.jsonPrimitive?.contentOrNull.isNullOrBlank()) errors += "days must be non-empty"
        }
        else -> errors += "unknown tool: $toolName"
    }
    return SchemaValidationResult(valid = errors.isEmpty(), errors = errors)
}

fun normalizeToolResult(toolName: String?, raw: String?): NormalizedToolResult? {
    if (toolName == null || raw.isNullOrBlank()) return null
    val summary = when (toolName) {
        "getCoinPrice" -> runCatching {
            val obj = JsonConfig.json.parseToJsonElement(raw).jsonObject
            val sym = obj["symbol"]?.jsonPrimitive?.contentOrNull ?: obj["coinId"]?.jsonPrimitive?.contentOrNull ?: "coin"
            val price = obj["price"]?.jsonPrimitive?.doubleOrNull ?: obj["priceUsd"]?.jsonPrimitive?.doubleOrNull
            val cur = obj["vsCurrency"]?.jsonPrimitive?.contentOrNull?.uppercase() ?: "USD"
            if (price != null) "$sym: ${"%.2f".format(price)} $cur" else raw.take(220)
        }.getOrElse { raw.take(220) }
        "getTrendingCoins" -> runCatching {
            val obj = JsonConfig.json.parseToJsonElement(raw).jsonObject
            val coins = obj["coins"]?.jsonArray.orEmpty()
            val top = coins.take(5).mapNotNull { coin ->
                val item = coin.jsonObject["item"]?.jsonObject ?: return@mapNotNull null
                val name = item["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val symbol = item["symbol"]?.jsonPrimitive?.contentOrNull?.uppercase() ?: "N/A"
                val rank = item["market_cap_rank"]?.jsonPrimitive?.intOrNull
                if (rank != null) "$name ($symbol, #$rank)" else "$name ($symbol)"
            }
            if (top.isEmpty()) {
                "Трендовые монеты получить не удалось."
            } else {
                "Топ трендов CoinGecko: ${top.joinToString(", ")}."
            }
        }.getOrElse { raw.take(220) }
        "getMarketSnapshot" -> runCatching {
            val arr = JsonConfig.json.parseToJsonElement(raw).jsonArray
            val top = arr.take(5).mapNotNull { item ->
                val obj = item.jsonObject
                val name = obj["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val symbol = obj["symbol"]?.jsonPrimitive?.contentOrNull?.uppercase() ?: "N/A"
                val price = obj["current_price"]?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
                "$name ($symbol): ${"%.2f".format(price)} USD"
            }
            if (top.isEmpty()) {
                "Срез рынка получить не удалось."
            } else {
                "Срез рынка по капитализации: ${top.joinToString("; ")}."
            }
        }.getOrElse { raw.take(220) }
        "getCoinOhlc" -> runCatching {
            val arr = JsonConfig.json.parseToJsonElement(raw).jsonArray
            val firstClose = arr.firstOrNull()?.jsonArray?.getOrNull(4)?.jsonPrimitive?.doubleOrNull
            val lastClose = arr.lastOrNull()?.jsonArray?.getOrNull(4)?.jsonPrimitive?.doubleOrNull
            val firstTs = arr.firstOrNull()?.jsonArray?.firstOrNull()?.jsonPrimitive?.longOrNull
            val lastTs = arr.lastOrNull()?.jsonArray?.firstOrNull()?.jsonPrimitive?.longOrNull
            if (firstClose != null && lastClose != null) {
                val direction = if (lastClose >= firstClose) "вырос" else "снизился"
                val span = if (firstTs != null && lastTs != null) "за период ${firstTs}..${lastTs}" else "за выбранный период"
                "По OHLC цена $direction c ${"%.2f".format(firstClose)} до ${"%.2f".format(lastClose)} USD $span."
            } else {
                "Получены OHLC данные для графика."
            }
        }.getOrElse { raw.take(220) }
        else -> raw.take(220)
    }
    return NormalizedToolResult(summary = summary, raw = raw)
}

fun extractQuoteCard(toolName: String?, toolResult: String?): QuoteCard? {
    if (toolName != "getCoinPrice" || toolResult.isNullOrBlank()) return null

    return runCatching {
        val obj = JsonConfig.json.parseToJsonElement(toolResult).jsonObject
        val coinId = obj["coinId"]?.jsonPrimitive?.content ?: return null
        val currency = obj["vsCurrency"]?.jsonPrimitive?.content ?: "usd"
        val price = obj["price"]?.jsonPrimitive?.doubleOrNull
            ?: obj["priceUsd"]?.jsonPrimitive?.doubleOrNull
            ?: return null

        QuoteCard(
            coinId = coinId,
            coinName = obj["coinName"]?.jsonPrimitive?.contentOrNull,
            symbol = obj["symbol"]?.jsonPrimitive?.contentOrNull,
            currency = currency.uppercase(),
            price = price,
            imageUrl = obj["imageUrl"]?.jsonPrimitive?.contentOrNull,
            change24h = obj["change24h"]?.jsonPrimitive?.doubleOrNull,
        )
    }.getOrNull()
}
