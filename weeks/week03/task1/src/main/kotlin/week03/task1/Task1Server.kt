package week03.task1

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import java.nio.file.Paths
import kotlinx.serialization.Serializable
import week03.shared.agent.AgentRuntime
import week03.shared.agent.MemoryLayerService
import week03.shared.config.AppEnv
import week03.shared.llm.ChatLlmClient
import week03.shared.llm.EchoChatClient
import week03.shared.llm.YandexGptClient
import week03.shared.model.AgentProfile
import week03.shared.model.AgentSettings
import week03.shared.model.MemoryMetrics
import week03.shared.model.MemorySnapshot
import week03.shared.model.MemoryTestScenario
import week03.shared.storage.LongTermStore
import week03.shared.storage.MetricsHistoryStore
import week03.shared.storage.ShortTermStore
import week03.shared.storage.WorkingMemoryStore

@Serializable
private data class ChatRequest(val message: String)

@Serializable
private data class PreviewClassifierRequest(
    val userMessage: String,
    val assistantReply: String,
)

@Serializable
private data class SelectProfileRequest(val profileId: String)

@Serializable
private data class ProfilesResponse(
    val profiles: List<AgentProfile>,
    val active_profile_id: String? = null,
)

@Serializable
private data class MemoryResponse(
    val short_term: week03.shared.model.ShortTermState,
    val working_memory: week03.shared.model.WorkingMemoryState,
    val long_term_memory: week03.shared.model.LongTermMemoryState,
)

@Serializable
private data class ChatResponse(
    val reply: String,
    val provider: String,
    val inputTokens: Int? = null,
    val outputTokens: Int? = null,
    val memory: MemoryMetrics,
)

@Serializable
private data class ProfilesSelectResponse(
    val ok: Boolean,
    val active_profile_id: String,
)

fun main() {
    val port = AppEnv.get("WEEK03_TASK1_PORT", "6001").toInt()
    embeddedServer(Netty, port = port, host = "127.0.0.1") {
        task1Module()
    }.start(wait = true)
}

fun Application.task1Module() {
    install(ContentNegotiation) {
        json()
    }

    val llmClient: ChatLlmClient = if (AppEnv.hasYandexCredentials()) {
        YandexGptClient()
    } else {
        EchoChatClient()
    }

    val shortTermStore = ShortTermStore(Paths.get("weeks/week03/task1/short_term.json"), maxMessages = 24)
    val workingMemoryStore = WorkingMemoryStore(Paths.get("weeks/week03/task1/working_memory.json"))
    val longTermStore = LongTermStore(Paths.get("weeks/week03/shared/long_term_memory.json"))
    val metricsHistoryStore = MetricsHistoryStore(Paths.get("weeks/week03/task1/metrics_history.json"))

    val runtime = AgentRuntime(
        llmClient = llmClient,
        memoryLayerService = MemoryLayerService(
            shortTermStore = shortTermStore,
            workingMemoryStore = workingMemoryStore,
            longTermStore = longTermStore,
        ),
        metricsHistoryStore = metricsHistoryStore,
        initialSettings = AgentSettings(
            systemPrompt = "You are a concise helpful AI assistant.",
            temperature = 0.3,
            maxTokens = 600,
        ),
    )

    routing {
        get("/") {
            call.respondText(renderTask1Page(), contentType = ContentType.Text.Html)
        }

        get("/api/profiles") {
            val profiles = runtime.listProfiles()
            val activeProfileId = runtime.getMemorySnapshot().longTerm.activeProfileId
            call.respond(
                ProfilesResponse(
                    profiles = profiles,
                    active_profile_id = activeProfileId,
                ),
            )
        }

        post("/api/profiles/select") {
            val payload = call.receive<SelectProfileRequest>()
            if (payload.profileId.isBlank()) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "profileId must not be blank"))
                return@post
            }
            val ok = runtime.selectProfile(payload.profileId)
            if (!ok) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "unknown profileId"))
                return@post
            }
            call.respond(ProfilesSelectResponse(ok = true, active_profile_id = payload.profileId))
        }

        get("/api/memory") {
            val snapshot = runtime.getMemorySnapshot()
            call.respond(
                MemoryResponse(
                    short_term = snapshot.shortTerm,
                    working_memory = snapshot.working,
                    long_term_memory = snapshot.longTerm,
                ),
            )
        }

        get("/api/history") {
            call.respond(mapOf("history" to runtime.getHistory()))
        }

        get("/api/metrics") {
            call.respond(mapOf("metrics_history" to runtime.getMetricsHistory()))
        }

        get("/api/eval-scenarios") {
            call.respond(mapOf("scenarios" to evaluationScenarios()))
        }

        post("/api/chat") {
            val payload = call.receive<ChatRequest>()
            if (payload.message.isBlank()) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "message must not be blank"))
                return@post
            }

            val result = runtime.sendUserMessage(payload.message)
            call.respond(
                ChatResponse(
                    reply = result.reply.text,
                    provider = result.reply.provider,
                    inputTokens = result.reply.inputTokens,
                    outputTokens = result.reply.outputTokens,
                    memory = result.memoryMetrics,
                ),
            )
        }

        post("/api/memory/classifier-preview") {
            val payload = call.receive<PreviewClassifierRequest>()
            if (payload.userMessage.isBlank() || payload.assistantReply.isBlank()) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "userMessage and assistantReply must not be blank"),
                )
                return@post
            }
            val decision = runtime.getClassifierPreview(
                userMessage = payload.userMessage,
                assistantReply = payload.assistantReply,
            )
            call.respond(mapOf("decision" to decision))
        }

        post("/api/reset") {
            val layer = call.request.queryParameters["layer"]?.trim()?.lowercase() ?: "all"
            if (layer !in setOf("all", "short", "working", "long")) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "layer must be all|short|working|long"))
                return@post
            }
            runtime.clearMemory(layer)
            call.respond(mapOf("ok" to true, "layer" to layer))
        }
    }
}

private fun evaluationScenarios(): List<MemoryTestScenario> {
    return listOf(
        MemoryTestScenario(
            id = "short-term-reference",
            title = "Short-term context reference",
            purpose = "Проверить, что агент помнит недавние реплики текущего диалога.",
            steps = listOf(
                "Отправить: 'Запомни кодовое слово: ORBIT'.",
                "Отправить 2-3 нейтральных сообщения.",
                "Спросить: 'Какое было кодовое слово?'",
            ),
            expected = "Ассистент возвращает ORBIT, используя short_term.",
        ),
        MemoryTestScenario(
            id = "working-goal-constraints",
            title = "Working memory with active task",
            purpose = "Проверить перенос цели/ограничений в рабочую память.",
            steps = listOf(
                "Отправить: 'Goal: сделать MVP за 2 недели'.",
                "Отправить: 'Constraints: бюджет 1000$, команда 2 человека'.",
                "Спросить: 'Собери план из 3 шагов с учетом ограничений'.",
            ),
            expected = "Ответ учитывает цель и ограничения, working_hits > 0.",
        ),
        MemoryTestScenario(
            id = "long-term-profile-persistence",
            title = "Long-term profile persistence",
            purpose = "Проверить влияние выбранного профильного long-term слоя.",
            steps = listOf(
                "Выбрать профиль 'Дизайнер квартир (современный интерьер)'.",
                "Задать вопрос про план ремонта комнаты.",
                "Переключить профиль на 'Мастер по электрике' и повторить вопрос.",
            ),
            expected = "Ответы отличаются по профилю и профессиональному фокусу.",
        ),
    )
}
