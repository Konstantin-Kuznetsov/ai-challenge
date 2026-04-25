package week03.task3

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import week03.shared.agent.MemoryLayerService
import week03.shared.agent.TaskStateMachineService
import week03.shared.llm.ChatLlmClient
import week03.shared.model.AgentSettings
import week03.shared.model.ChatMessage
import week03.shared.model.MemoryWriteDecision
import week03.shared.model.TaskStage
import week03.shared.model.TaskStateContext
import week03.shared.storage.TaskStateStore

class Task3Runtime(
    private val llmClient: ChatLlmClient,
    private val memoryLayerService: MemoryLayerService,
    private val taskStateStore: TaskStateStore,
    private val fsm: TaskStateMachineService,
    private val settings: AgentSettings,
) {
    private val mutex = Mutex()
    private val totalSteps = 4

    suspend fun sendUserMessage(text: String): Pair<String, String?> = mutex.withLock {
        val state = normalizeState(taskStateStore.load())
        val command = parseCommand(text)

        if (command != ChatCommand.RESUME && state.paused) {
            val pausedReply = formatStateText(
                state,
                "Пауза активна. Напишите `продолжай`, чтобы возобновить шаг ${state.currentStep}/${state.totalSteps}.",
            )
            memoryLayerService.persistTurn(text, pausedReply, MemoryWriteDecision())
            return@withLock pausedReply to null
        }

        if (command != null) {
            val commandReply = handleCommand(command, state, text)
            return@withLock commandReply to null
        }

        val contextBundle = memoryLayerService.buildContext()
        val fsmConstraints = buildString {
            append("Task state machine constraints:\n")
            append("- current_state: ${state.stage}\n")
            append("- current_step: ${state.currentStep}/${state.totalSteps}\n")
            append("- expected_action: ${state.expectedAction}\n")
            append("- allowed_transitions: ${fsm.allowedTargets(state.stage).joinToString(", ")}\n")
            append("- do_not_skip_stages: true\n")
            append("- if_step_completed: suggest next step only within allowed transitions\n")
        }

        val messages = contextBundle.shortTermMessagesForPrompt + ChatMessage(role = "user", content = text)
        val reply = llmClient.complete(
            messages = messages,
            settings = settings.copy(
                systemPrompt = (listOf(settings.systemPrompt, fsmConstraints) + contextBundle.systemContextBlocks)
                    .joinToString("\n\n"),
            ),
        )
        val classifyDecision = llmClient.classifyMemoryWrites(text, reply.text, memoryLayerService.snapshot())
        val decoratedReply = formatStateText(state, reply.text)
        memoryLayerService.persistTurn(text, decoratedReply, classifyDecision)
        taskStateStore.save(state)
        return@withLock decoratedReply to null
    }

    suspend fun getHistory(): List<ChatMessage> = mutex.withLock {
        memoryLayerService.snapshot().shortTerm.messages
    }

    suspend fun getTaskState(): TaskStateContext = mutex.withLock { taskStateStore.load() }

    suspend fun transition(target: TaskStage, expectedAction: String?): String = mutex.withLock {
        val state = normalizeState(taskStateStore.load())
        val result = fsm.transition(state, target)
        val next = applyStageDefaults(
            context = result.context,
            expectedActionOverride = expectedAction,
        )
        taskStateStore.save(next)
        val msg = formatStateText(next, result.message)
        memoryLayerService.appendSystemMessage(msg)
        msg
    }

    suspend fun pause(): String = mutex.withLock {
        val result = fsm.pause(normalizeState(taskStateStore.load()))
        taskStateStore.save(result.context)
        val msg = formatStateText(result.context, result.message)
        memoryLayerService.appendSystemMessage(msg)
        msg
    }

    suspend fun resume(): String = mutex.withLock {
        val result = fsm.resume(normalizeState(taskStateStore.load()))
        taskStateStore.save(result.context)
        val msg = formatStateText(result.context, result.message)
        memoryLayerService.appendSystemMessage(msg)
        msg
    }

    suspend fun resetLayer(layer: String) = mutex.withLock {
        when (layer) {
            "short" -> {
                memoryLayerService.clearShortTerm()
                taskStateStore.reset()
            }
            "working" -> memoryLayerService.clearWorkingMemory()
            "long" -> memoryLayerService.clearLongTerm()
            "all" -> {
                memoryLayerService.clearShortTerm()
                memoryLayerService.clearWorkingMemory()
                memoryLayerService.clearLongTerm()
                taskStateStore.reset()
            }
            else -> error("Unknown layer")
        }
    }

    suspend fun getMemorySnapshot() = mutex.withLock { memoryLayerService.snapshot() }
    suspend fun listProfiles() = mutex.withLock { memoryLayerService.listProfiles() }
    suspend fun selectProfile(profileId: String) = mutex.withLock { memoryLayerService.selectProfile(profileId) }

    private fun handleCommand(command: ChatCommand, state: TaskStateContext, userText: String): String {
        return when (command) {
            ChatCommand.PAUSE -> {
                val result = fsm.pause(state)
                taskStateStore.save(result.context)
                val msg = formatStateText(result.context, "Пауза включена.")
                memoryLayerService.persistTurn(userText, msg, MemoryWriteDecision())
                msg
            }
            ChatCommand.RESUME -> {
                val result = fsm.resume(state)
                taskStateStore.save(result.context)
                val msg = formatStateText(result.context, "Продолжаем: ${result.context.current}")
                memoryLayerService.persistTurn(userText, msg, MemoryWriteDecision())
                msg
            }
            ChatCommand.NEXT -> {
                val allowed = fsm.allowedTargets(state.stage).toList()
                if (allowed.isEmpty()) {
                    val msg = formatStateText(state, "Дальше переходить некуда: состояние DONE.")
                    memoryLayerService.persistTurn(userText, msg, MemoryWriteDecision())
                    return msg
                }
                val target = when (state.stage) {
                    TaskStage.PLANNING -> TaskStage.EXECUTION
                    TaskStage.EXECUTION -> TaskStage.VALIDATION
                    TaskStage.VALIDATION -> TaskStage.DONE
                    TaskStage.DONE -> TaskStage.DONE
                }
                if (target == TaskStage.DONE && state.stage == TaskStage.DONE) {
                    val msg = formatStateText(state, "Задача уже находится в DONE.")
                    memoryLayerService.persistTurn(userText, msg, MemoryWriteDecision())
                    return msg
                }
                if (!fsm.canTransition(state.stage, target)) {
                    val fallback = allowed.first()
                    val transitionedFallback = fsm.transition(state, fallback)
                    val nextFallback = applyStageDefaults(transitionedFallback.context, null)
                    taskStateStore.save(nextFallback)
                    val fallbackMsg = formatStateText(
                        nextFallback,
                        "${state.stage} завершен, переходим к ${nextFallback.stage}.",
                    )
                    memoryLayerService.persistTurn(userText, fallbackMsg, MemoryWriteDecision())
                    return fallbackMsg
                }
                val transitioned = fsm.transition(state, target)
                val next = applyStageDefaults(transitioned.context, null)
                taskStateStore.save(next)
                val msg = formatStateText(next, "${state.stage} завершен, переходим к ${next.stage}.")
                memoryLayerService.persistTurn(userText, msg, MemoryWriteDecision())
                msg
            }
            ChatCommand.BACK -> {
                val target = TaskStage.PLANNING
                if (!fsm.canTransition(state.stage, target)) {
                    val msg = formatStateText(
                        state,
                        "Откат в PLANNING недоступен из ${state.stage}. Допустимо: ${fsm.allowedTargets(state.stage)}",
                    )
                    memoryLayerService.persistTurn(userText, msg, MemoryWriteDecision())
                    return msg
                }
                val transitioned = fsm.transition(state, target)
                val next = applyStageDefaults(transitioned.context, null)
                taskStateStore.save(next)
                val msg = formatStateText(next, "Возвращаемся к PLANNING.")
                memoryLayerService.persistTurn(userText, msg, MemoryWriteDecision())
                msg
            }
            ChatCommand.DONE -> {
                val target = TaskStage.DONE
                if (!fsm.canTransition(state.stage, target)) {
                    val msg = formatStateText(
                        state,
                        "Переход в DONE недоступен из ${state.stage}. Сначала перейдите через допустимые этапы.",
                    )
                    memoryLayerService.persistTurn(userText, msg, MemoryWriteDecision())
                    return msg
                }
                val transitioned = fsm.transition(state, target)
                val next = applyStageDefaults(transitioned.context, "Подвести итог и зафиксировать результат.")
                taskStateStore.save(next)
                val msg = formatStateText(next, "Задача переведена в DONE.")
                memoryLayerService.persistTurn(userText, msg, MemoryWriteDecision())
                msg
            }
        }
    }

    private fun normalizeState(context: TaskStateContext): TaskStateContext {
        if (context.task.isBlank()) {
            return applyStageDefaults(
                TaskStateContext(
                    task = "Task03 state machine demo",
                    stage = TaskStage.PLANNING,
                    currentStep = 1,
                    totalSteps = totalSteps,
                    plan = listOf("Собрать требования", "Реализовать", "Провести валидацию", "Зафиксировать результат"),
                    done = emptyList(),
                    current = "",
                    expectedAction = "",
                    paused = false,
                ),
                null,
            )
        }
        if (context.totalSteps <= 0) {
            return applyStageDefaults(context.copy(totalSteps = totalSteps), context.expectedAction)
        }
        return context
    }

    private fun applyStageDefaults(context: TaskStateContext, expectedActionOverride: String?): TaskStateContext {
        val step = when (context.stage) {
            TaskStage.PLANNING -> 1
            TaskStage.EXECUTION -> 2
            TaskStage.VALIDATION -> 3
            TaskStage.DONE -> 4
        }
        val (current, expected) = when (context.stage) {
            TaskStage.PLANNING -> "Собираем требования и утверждаем план." to "Сформулировать и утвердить план."
            TaskStage.EXECUTION -> "Выполняем шаги плана." to "Выполнить текущий шаг без перескока этапов."
            TaskStage.VALIDATION -> "Проверяем результат и соответствие плану." to "Провести проверку и собрать замечания."
            TaskStage.DONE -> "Задача завершена." to "Зафиксировать итоговый результат."
        }
        return context.copy(
            currentStep = step,
            totalSteps = totalSteps,
            current = current,
            expectedAction = expectedActionOverride?.takeIf { it.isNotBlank() } ?: expected,
        )
    }

    private fun formatStateText(state: TaskStateContext, body: String): String {
        return "[${state.stage} ${state.currentStep}/${state.totalSteps}] $body"
    }

    private fun parseCommand(text: String): ChatCommand? {
        val normalized = text.trim().lowercase()
        return when (normalized) {
            "/pause", "pause", "пауза", "стоп", "поставь на паузу" -> ChatCommand.PAUSE
            "/resume", "resume", "продолжай", "продолжить" -> ChatCommand.RESUME
            "/next", "next", "дальше", "следующий этап", "следующий шаг" -> ChatCommand.NEXT
            "/back", "back", "назад", "вернись к planning", "откат" -> ChatCommand.BACK
            "/done", "done", "заверши", "завершить" -> ChatCommand.DONE
            else -> null
        }
    }
}

private enum class ChatCommand {
    PAUSE,
    RESUME,
    NEXT,
    BACK,
    DONE,
}
