package week03.shared.storage

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import week03.shared.model.TaskStage
import week03.shared.model.TaskStateContext

class TaskStateStore(private val path: Path) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    fun load(): TaskStateContext {
        if (!path.exists()) {
            return defaultPlanningContext()
        }
        return try {
            val raw = Files.readString(path)
            if (raw.isBlank()) defaultPlanningContext() else json.decodeFromString(raw)
        } catch (_: Exception) {
            defaultPlanningContext()
        }
    }

    fun save(context: TaskStateContext) {
        Files.createDirectories(path.parent)
        Files.writeString(path, json.encodeToString(context))
    }

    fun reset() = save(defaultPlanningContext())

    private fun defaultPlanningContext(): TaskStateContext {
        return TaskStateContext(
            task = "Task03 state machine demo",
            stage = TaskStage.PLANNING,
            currentStep = 1,
            totalSteps = 4,
            plan = listOf(
                "Собрать требования",
                "Реализовать",
                "Провести валидацию",
                "Зафиксировать результат",
            ),
            done = emptyList(),
            current = "Собираем требования и утверждаем план.",
            expectedAction = "Сформулировать и утвердить план.",
            paused = false,
        )
    }
}
