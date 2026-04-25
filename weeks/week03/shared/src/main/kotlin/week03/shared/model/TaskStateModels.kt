package week03.shared.model

import kotlinx.serialization.Serializable

@Serializable
enum class TaskStage {
    PLANNING,
    EXECUTION,
    VALIDATION,
    DONE,
}

@Serializable
data class TaskStateContext(
    val task: String = "",
    val stage: TaskStage = TaskStage.PLANNING,
    val currentStep: Int = 1,
    val totalSteps: Int = 1,
    val plan: List<String> = emptyList(),
    val done: List<String> = emptyList(),
    val current: String = "",
    val expectedAction: String = "",
    val paused: Boolean = false,
)

@Serializable
data class TaskStateTransitionResult(
    val context: TaskStateContext,
    val message: String,
)
