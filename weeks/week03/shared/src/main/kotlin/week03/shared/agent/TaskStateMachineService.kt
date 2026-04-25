package week03.shared.agent

import week03.shared.model.TaskStage
import week03.shared.model.TaskStateContext
import week03.shared.model.TaskStateTransitionResult

class TaskStateMachineService {
    private val transitions: Map<TaskStage, Set<TaskStage>> = mapOf(
        TaskStage.PLANNING to setOf(TaskStage.EXECUTION),
        TaskStage.EXECUTION to setOf(TaskStage.PLANNING, TaskStage.VALIDATION),
        TaskStage.VALIDATION to setOf(TaskStage.PLANNING, TaskStage.DONE),
        TaskStage.DONE to emptySet(),
    )

    fun allowedTargets(from: TaskStage): Set<TaskStage> = transitions.getValue(from)

    fun canTransition(from: TaskStage, to: TaskStage): Boolean = to in allowedTargets(from)

    fun transition(context: TaskStateContext, target: TaskStage): TaskStateTransitionResult {
        require(canTransition(context.stage, target)) {
            "Transition ${context.stage} -> $target is not allowed."
        }
        val next = context.copy(stage = target, paused = false)
        return TaskStateTransitionResult(
            context = next,
            message = "${context.stage} завершен, переходим к $target",
        )
    }

    fun pause(context: TaskStateContext): TaskStateTransitionResult {
        return TaskStateTransitionResult(
            context = context.copy(paused = true),
            message = "Пауза включена на этапе ${context.stage}, шаг ${context.currentStep}/${context.totalSteps}.",
        )
    }

    fun resume(context: TaskStateContext): TaskStateTransitionResult {
        return TaskStateTransitionResult(
            context = context.copy(paused = false),
            message = "Продолжаем этап ${context.stage}, шаг ${context.currentStep}/${context.totalSteps}: ${context.current}",
        )
    }
}
