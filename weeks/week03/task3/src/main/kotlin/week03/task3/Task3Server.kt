package week03.task3

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
import week03.shared.agent.MemoryLayerService
import week03.shared.agent.TaskStateMachineService
import week03.shared.config.AppEnv
import week03.shared.llm.ChatLlmClient
import week03.shared.llm.EchoChatClient
import week03.shared.llm.YandexGptClient
import week03.shared.model.AgentSettings
import week03.shared.model.AgentProfile
import week03.shared.model.MemorySnapshot
import week03.shared.model.TaskStage
import week03.shared.model.TaskStateContext
import week03.shared.storage.LongTermStore
import week03.shared.storage.ShortTermStore
import week03.shared.storage.TaskStateStore
import week03.shared.storage.WorkingMemoryStore

@Serializable
private data class ChatRequest(val message: String)

@Serializable
private data class SelectProfileRequest(val profileId: String)

@Serializable
private data class TransitionRequest(
    val target: TaskStage,
    val expectedAction: String? = null,
)

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
    val transitionMessage: String? = null,
)

@Serializable
private data class SimpleMessageResponse(val message: String)

@Serializable
private data class StateResponse(
    val task: String,
    val stage: TaskStage,
    val currentStep: Int,
    val totalSteps: Int,
    val expectedAction: String,
    val current: String,
    val paused: Boolean,
    val plan: List<String>,
    val done: List<String>,
)

fun main() {
    val port = AppEnv.get("WEEK03_TASK3_PORT", "6003").toInt()
    embeddedServer(Netty, port = port, host = "127.0.0.1") {
        task3Module()
    }.start(wait = true)
}

fun Application.task3Module() {
    install(ContentNegotiation) { json() }

    val llmClient: ChatLlmClient = if (AppEnv.hasYandexCredentials()) YandexGptClient() else EchoChatClient()

    val memoryLayerService = MemoryLayerService(
        shortTermStore = ShortTermStore(Paths.get("weeks/week03/task3/short_term.json"), maxMessages = 30),
        workingMemoryStore = WorkingMemoryStore(Paths.get("weeks/week03/task3/working_memory.json")),
        longTermStore = LongTermStore(Paths.get("weeks/week03/shared/long_term_memory.json")),
    )
    val stateStore = TaskStateStore(Paths.get("weeks/week03/task3/task_state.json"))
    if (stateStore.load().task.isBlank()) {
        stateStore.save(
            TaskStateContext(
                task = "Task03 state machine demo",
                stage = TaskStage.PLANNING,
                currentStep = 1,
                totalSteps = 4,
                current = "Собираем требования и утверждаем план.",
                expectedAction = "Сформулировать план задачи и критерии выполнения.",
                plan = listOf(
                    "Собрать требования",
                    "Реализовать",
                    "Провести валидацию",
                    "Зафиксировать результат",
                ),
            ),
        )
    }

    val runtime = Task3Runtime(
        llmClient = llmClient,
        memoryLayerService = memoryLayerService,
        taskStateStore = stateStore,
        fsm = TaskStateMachineService(),
        settings = AgentSettings(
            systemPrompt = "You are a strict task-oriented assistant. Follow task state constraints exactly.",
            temperature = 0.2,
            maxTokens = 600,
        ),
    )

    routing {
        get("/") { call.respondText(renderTask3Page(), ContentType.Text.Html) }

        get("/api/history") { call.respond(mapOf("history" to runtime.getHistory())) }

        get("/api/profiles") {
            val active = runtime.getMemorySnapshot().longTerm.activeProfileId
            call.respond(ProfilesResponse(runtime.listProfiles(), active))
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
            call.respond(mapOf("ok" to true))
        }

        get("/api/memory") {
            val mem = runtime.getMemorySnapshot()
            call.respond(MemoryResponse(mem.shortTerm, mem.working, mem.longTerm))
        }

        get("/api/task-state") {
            call.respond(runtime.getTaskState().toResponse())
        }

        post("/api/task-state/transition") {
            val payload = call.receive<TransitionRequest>()
            val msg = runtime.transition(payload.target, payload.expectedAction)
            call.respond(SimpleMessageResponse(msg))
        }

        post("/api/task-state/pause") { call.respond(SimpleMessageResponse(runtime.pause())) }
        post("/api/task-state/resume") { call.respond(SimpleMessageResponse(runtime.resume())) }

        post("/api/chat") {
            val req = call.receive<ChatRequest>()
            if (req.message.isBlank()) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "message must not be blank"))
                return@post
            }
            val (reply, transitionMessage) = runtime.sendUserMessage(req.message)
            call.respond(ChatResponse(reply = reply, transitionMessage = transitionMessage))
        }

        post("/api/reset") {
            val layer = call.request.queryParameters["layer"]?.trim()?.lowercase() ?: "all"
            if (layer !in setOf("all", "short", "working", "long")) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "layer must be all|short|working|long"))
                return@post
            }
            runtime.resetLayer(layer)
            call.respond(mapOf("ok" to true))
        }
    }
}

private fun TaskStateContext.toResponse(): StateResponse = StateResponse(
    task = task,
    stage = stage,
    currentStep = currentStep,
    totalSteps = totalSteps,
    expectedAction = expectedAction,
    current = current,
    paused = paused,
    plan = plan,
    done = done,
)
