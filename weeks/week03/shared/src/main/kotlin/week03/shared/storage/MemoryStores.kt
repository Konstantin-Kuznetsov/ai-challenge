package week03.shared.storage

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import week03.shared.model.AgentProfile
import week03.shared.model.ChatMessage
import week03.shared.model.LongTermMemoryState
import week03.shared.model.MemoryLayer
import week03.shared.model.MemoryWriteCandidate
import week03.shared.model.ShortTermState
import week03.shared.model.WorkingMemoryState

private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

class ShortTermStore(
    private val path: Path,
    private val maxMessages: Int = 20,
) {
    fun load(): ShortTermState {
        val state = readOrDefault(path, ShortTermState())
        return state.copy(messages = state.messages.takeLast(maxMessages))
    }

    fun save(state: ShortTermState) {
        val normalized = state.copy(messages = state.messages.takeLast(maxMessages))
        write(path, normalized)
    }

    fun appendUserAssistant(userText: String, assistantText: String): Int {
        val current = load().messages.toMutableList()
        current.add(ChatMessage(role = "user", content = userText))
        current.add(ChatMessage(role = "assistant", content = assistantText))
        save(ShortTermState(messages = current))
        return 2
    }

    fun clear() = save(ShortTermState())
}

class WorkingMemoryStore(private val path: Path) {
    fun load(): WorkingMemoryState = readOrDefault(path, WorkingMemoryState())

    fun save(state: WorkingMemoryState) {
        write(path, state)
    }

    fun apply(candidates: List<MemoryWriteCandidate>): Int {
        var writes = 0
        var state = load()
        candidates.forEach { candidate ->
            if (candidate.layer != MemoryLayer.WORKING) {
                return@forEach
            }

            val value = normalize(candidate.value) ?: return@forEach
            when (candidate.key?.trim()?.lowercase()) {
                "goal" -> {
                    if (state.goal != value) {
                        state = state.copy(goal = value)
                        writes++
                    }
                }
                "constraint", "constraints" -> {
                    val updated = appendUnique(state.constraints, value)
                    if (updated != state.constraints) {
                        state = state.copy(constraints = updated.takeLast(12))
                        writes++
                    }
                }
                "decision", "decisions" -> {
                    val updated = appendUnique(state.decisions, value)
                    if (updated != state.decisions) {
                        state = state.copy(decisions = updated.takeLast(12))
                        writes++
                    }
                }
                "open_question", "question", "open_questions" -> {
                    val updated = appendUnique(state.openQuestions, value)
                    if (updated != state.openQuestions) {
                        state = state.copy(openQuestions = updated.takeLast(12))
                        writes++
                    }
                }
                else -> {
                    val updated = appendUnique(state.notes, value)
                    if (updated != state.notes) {
                        state = state.copy(notes = updated.takeLast(20))
                        writes++
                    }
                }
            }
        }
        save(state)
        return writes
    }

    fun clear() = save(WorkingMemoryState())
}

class LongTermStore(private val path: Path) {
    fun load(): LongTermMemoryState = readOrDefault(path, LongTermMemoryState())

    fun save(state: LongTermMemoryState) {
        write(path, state.copy(updatedAtEpochMs = System.currentTimeMillis()))
    }

    fun setProfile(profile: AgentProfile): Int {
        val current = load()
        val next = LongTermMemoryState(
            activeProfileId = profile.id,
            profile = mapOf(
                "id" to profile.id,
                "title" to profile.title,
                "specialization" to profile.specialization,
                "roleSummary" to profile.roleSummary,
            ),
            knowledge = profile.baselineRequirements,
            stableDecisions = profile.styleRules,
            updatedAtEpochMs = current.updatedAtEpochMs,
        )
        val changed = current != next
        save(next)
        return if (changed) 1 else 0
    }

    fun clear() = save(LongTermMemoryState())
}

private fun normalize(value: String): String? {
    val sanitized = value.trim().replace(Regex("\\s+"), " ")
    if (sanitized.isBlank()) {
        return null
    }
    if (sanitized.length > 280) {
        return sanitized.take(280)
    }
    return sanitized
}

private fun appendUnique(items: List<String>, value: String): List<String> {
    if (items.any { it.equals(value, ignoreCase = true) }) {
        return items
    }
    return items + value
}

private inline fun <reified T> readOrDefault(path: Path, fallback: T): T {
    if (!path.exists()) {
        return fallback
    }
    return try {
        val raw = Files.readString(path)
        if (raw.isBlank()) fallback else json.decodeFromString<T>(raw)
    } catch (_: Exception) {
        fallback
    }
}

private inline fun <reified T> write(path: Path, value: T) {
    Files.createDirectories(path.parent)
    Files.writeString(path, json.encodeToString(value))
}
