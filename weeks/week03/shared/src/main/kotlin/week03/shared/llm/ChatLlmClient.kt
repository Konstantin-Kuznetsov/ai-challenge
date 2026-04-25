package week03.shared.llm

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.serialization.decodeFromString
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import week03.shared.config.AppEnv
import week03.shared.model.AgentReply
import week03.shared.model.AgentSettings
import week03.shared.model.ChatMessage
import week03.shared.model.MemoryLayer
import week03.shared.model.MemorySnapshot
import week03.shared.model.MemoryWriteCandidate
import week03.shared.model.MemoryWriteDecision

interface ChatLlmClient {
    suspend fun complete(messages: List<ChatMessage>, settings: AgentSettings): AgentReply
    suspend fun classifyMemoryWrites(
        userMessage: String,
        assistantReply: String,
        snapshot: MemorySnapshot,
    ): MemoryWriteDecision
}

class EchoChatClient : ChatLlmClient {
    override suspend fun complete(messages: List<ChatMessage>, settings: AgentSettings): AgentReply {
        val userText = messages.lastOrNull { it.role == "user" }?.content ?: ""
        return AgentReply(
            text = "Echo mode: $userText",
            provider = "echo",
        )
    }

    override suspend fun classifyMemoryWrites(
        userMessage: String,
        assistantReply: String,
        snapshot: MemorySnapshot,
    ): MemoryWriteDecision {
        val candidates = mutableListOf<MemoryWriteCandidate>()
        val text = userMessage.lowercase()
        if ("goal:" in text) {
            candidates.add(
                MemoryWriteCandidate(
                    layer = MemoryLayer.WORKING,
                    key = "goal",
                    value = userMessage.substringAfter("goal:", "").trim(),
                    reason = "User explicitly provided current goal.",
                    confidence = 0.85,
                ),
            )
        }
        if ("constraint" in text || "огранич" in text) {
            candidates.add(
                MemoryWriteCandidate(
                    layer = MemoryLayer.WORKING,
                    key = "constraints",
                    value = userMessage.trim(),
                    reason = "Message includes constraints for current task.",
                    confidence = 0.72,
                ),
            )
        }
        return MemoryWriteDecision(candidates = candidates)
    }
}

class YandexGptClient(
    private val httpClient: HttpClient = HttpClient {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    },
) : ChatLlmClient {
    override suspend fun complete(messages: List<ChatMessage>, settings: AgentSettings): AgentReply {
        val apiKey = AppEnv.require("YANDEX_API_KEY")
        val folderId = AppEnv.require("YANDEX_FOLDER_ID")
        val model = AppEnv.get("YANDEX_MODEL", "yandexgpt-lite")

        val body = buildJsonObject {
            put("modelUri", "gpt://$folderId/$model")
            putJsonObject("completionOptions") {
                put("stream", false)
                put("temperature", settings.temperature)
                put("maxTokens", settings.maxTokens.toString())
            }
            put("messages", buildJsonArray {
                add(buildJsonObject {
                    put("role", "system")
                    put("text", settings.systemPrompt)
                })
                messages.forEach { message ->
                    add(buildJsonObject {
                        put("role", message.role)
                        put("text", message.content)
                    })
                }
            })
        }

        val response = httpClient.post("https://llm.api.cloud.yandex.net/foundationModels/v1/completion") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Api-Key $apiKey")
            setBody(body)
        }

        val payload = response.body<JsonObject>()
        val alternative = payload["result"]
            ?.jsonObject
            ?.get("alternatives")
            ?.jsonArray
            ?.firstOrNull()
            ?.jsonObject

        val replyText = alternative
            ?.get("message")
            ?.jsonObject
            ?.get("text")
            ?.jsonPrimitive
            ?.content
            ?.trim()
            .orEmpty()

        val usage = payload["result"]?.jsonObject?.get("usage")?.jsonObject

        return AgentReply(
            text = replyText.ifBlank { "Empty response from provider." },
            inputTokens = usage?.get("inputTextTokens")?.jsonPrimitive?.intOrNull,
            outputTokens = usage?.get("completionTokens")?.jsonPrimitive?.intOrNull,
            provider = "yandex",
        )
    }

    override suspend fun classifyMemoryWrites(
        userMessage: String,
        assistantReply: String,
        snapshot: MemorySnapshot,
    ): MemoryWriteDecision {
        val apiKey = AppEnv.require("YANDEX_API_KEY")
        val folderId = AppEnv.require("YANDEX_FOLDER_ID")
        val model = AppEnv.get("YANDEX_MODEL", "yandexgpt-lite")
        val jsonFormat = Json { ignoreUnknownKeys = true }

        val snapshotText = buildString {
            append("short_messages=")
            append(snapshot.shortTerm.messages.size)
            append("\nworking_goal=")
            append(snapshot.working.goal ?: "")
            append("\nworking_constraints=")
            append(snapshot.working.constraints.joinToString("; "))
            append("\nworking_decisions=")
            append(snapshot.working.decisions.joinToString("; "))
            append("\nlong_profile=")
            append(snapshot.longTerm.profile.entries.joinToString("; ") { "${it.key}=${it.value}" })
            append("\nlong_knowledge=")
            append(snapshot.longTerm.knowledge.joinToString("; "))
            append("\nlong_stable_decisions=")
            append(snapshot.longTerm.stableDecisions.joinToString("; "))
        }

        val classifierPrompt = """
            You classify what should be written to assistant memory.
            Return JSON only, without markdown fences.
            Schema:
            {
              "candidates":[
                {"layer":"WORKING","key":"goal|constraints|decisions|open_questions|notes","value":"...", "reason":"...", "confidence":0.0}
              ]
            }
            Rules:
            - SHORT_TERM is written outside this classifier.
            - WORKING for current task details.
            - LONG_TERM is managed by selected profile in UI, never write LONG_TERM here.
            - If nothing should be saved, return {"candidates":[]}.
        """.trimIndent()

        val body = buildJsonObject {
            put("modelUri", "gpt://$folderId/$model")
            putJsonObject("completionOptions") {
                put("stream", false)
                put("temperature", 0.0)
                put("maxTokens", "400")
            }
            put("messages", buildJsonArray {
                add(buildJsonObject {
                    put("role", "system")
                    put("text", classifierPrompt)
                })
                add(buildJsonObject {
                    put("role", "user")
                    put(
                        "text",
                        """
                        Current memory snapshot:
                        $snapshotText
                        
                        User message:
                        $userMessage
                        
                        Assistant reply:
                        $assistantReply
                        
                        Decide memory writes now.
                        """.trimIndent(),
                    )
                })
            })
        }

        val response = httpClient.post("https://llm.api.cloud.yandex.net/foundationModels/v1/completion") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Api-Key $apiKey")
            setBody(body)
        }
        val payload = response.body<JsonObject>()
        val text = payload["result"]
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

        return parseMemoryDecision(text, jsonFormat)
    }

    private fun parseMemoryDecision(text: String, jsonFormat: Json): MemoryWriteDecision {
        if (text.isBlank()) {
            return MemoryWriteDecision()
        }

        val normalized = text
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()

        return try {
            jsonFormat.decodeFromString<MemoryWriteDecision>(normalized)
        } catch (_: Exception) {
            MemoryWriteDecision()
        }
    }
}
