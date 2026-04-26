package week03.task4

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
import io.ktor.server.routing.put
import io.ktor.server.routing.routing
import java.nio.file.Paths
import kotlinx.serialization.Serializable
import week03.shared.agent.AgentRuntime
import week03.shared.agent.InvariantsStoreTurnGuard
import week03.shared.agent.MemoryLayerService
import week03.shared.config.AppEnv
import week03.shared.llm.ChatLlmClient
import week03.shared.llm.EchoChatClient
import week03.shared.llm.YandexGptClient
import week03.shared.model.AgentProfile
import week03.shared.model.AgentSettings
import week03.shared.model.InvariantsState
import week03.shared.model.MemoryMetrics
import week03.shared.model.TurnPipelineStep
import week03.shared.model.TurnValidationInfo
import week03.shared.storage.InvariantsStore
import week03.shared.storage.LongTermStore
import week03.shared.storage.MetricsHistoryStore
import week03.shared.storage.ShortTermStore
import week03.shared.storage.WorkingMemoryStore

@Serializable
private data class ChatRequest(val message: String)

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
    val pipeline: List<TurnPipelineStep> = emptyList(),
    val graph_edges_valid: Boolean = true,
    val validation: TurnValidationInfo? = null,
)

fun main() {
    val port = AppEnv.get("WEEK03_TASK4_PORT", "6004").toInt()
    embeddedServer(Netty, port = port, host = "127.0.0.1") {
        task4Module()
    }.start(wait = true)
}

fun Application.task4Module() {
    install(ContentNegotiation) {
        json()
    }

    val invariantsStore = InvariantsStore(Paths.get("weeks/week03/task4/invariants.json"))
    val invariantGuard = InvariantsStoreTurnGuard(invariantsStore)

    val llmClient: ChatLlmClient = if (AppEnv.hasYandexCredentials()) {
        YandexGptClient()
    } else {
        EchoChatClient()
    }

    val shortTermStore = ShortTermStore(Paths.get("weeks/week03/task4/short_term.json"), maxMessages = 24)
    val workingMemoryStore = WorkingMemoryStore(Paths.get("weeks/week03/task4/working_memory.json"))
    val longTermStore = LongTermStore(Paths.get("weeks/week03/shared/long_term_memory.json"))
    val metricsHistoryStore = MetricsHistoryStore(Paths.get("weeks/week03/task4/metrics_history.json"))

    val runtime = AgentRuntime(
        llmClient = llmClient,
        memoryLayerService = MemoryLayerService(
            shortTermStore = shortTermStore,
            workingMemoryStore = workingMemoryStore,
            longTermStore = longTermStore,
        ),
        metricsHistoryStore = metricsHistoryStore,
        initialSettings = AgentSettings(
            systemPrompt = """
Ты — ассистент по планированию поездок. Отвечай на языке пользователя (если пишут по-русски — по-русски).

Приоритеты и тон бери из блока памяти «Long-term memory» (выбранный профиль путешественника) и из инвариантов ниже: не противоречь им.

Когда пользователь просит маршрут, программу отпуска, варианты перелёта/отеля, «план», «итоговый план», «давай план» или по смыслу ждёт конкретику поездки:
- В том же ответе дай **готовый черновик плана**: перелёты (логистика, класс обслуживания в духе профиля, стыковки если уместно), отели (район/уровень в духе профиля), каркас по дням или по шагам «что забронировать первым», ориентиры цен только как «от … / порядок величины» без вымышленных точных сумм и без фиктивных броней.
- **Запрещено** заканчивать ответ одной отмазкой вроде «сейчас подбираю варианты, подождите» без списка конкретных шагов и названий. Если данных мало — кратко перечисли **явные допущения** (например даты или город вылета) и всё равно выдай **используемый** черновик плана на этих допущениях.
- Уточняющих вопросов не больше **двух**, и только вместе с уже данным частичным планом, а не вместо него.

Стиль: структурированно (заголовки/маркеры), без длинных извинений и без обещаний «вернусь позже».
            """.trimIndent(),
            temperature = 0.28,
            maxTokens = 1100,
        ),
        preMemoryContextProvider = {
            val block = invariantsStore.formatPromptBlock()
            if (block.isBlank()) emptyList() else listOf(block)
        },
        invariantTurnGuard = invariantGuard,
    )

    routing {
        get("/") {
            call.respondText(renderTask4Page(), contentType = ContentType.Text.Html)
        }

        get("/api/invariants") {
            call.respond(invariantsStore.load())
        }

        put("/api/invariants") {
            val body = call.receive<InvariantsState>()
            invariantsStore.save(body)
            call.respond(mapOf("ok" to true))
        }

        post("/api/invariants/reset") {
            invariantsStore.resetToDefaults()
            call.respond(invariantsStore.load())
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
            call.respond(mapOf("ok" to true, "active_profile_id" to payload.profileId))
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
                    pipeline = result.pipeline,
                    graph_edges_valid = result.graphEdgesValid,
                    validation = result.validation,
                ),
            )
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
