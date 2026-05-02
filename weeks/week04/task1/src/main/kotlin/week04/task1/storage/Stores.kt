package week04.task1.storage

import kotlinx.serialization.json.JsonObject
import week04.task1.config.JsonConfig
import week04.task1.model.ToolCallRecord
import week04.task1.model.TurnTrace
import java.nio.file.Files
import java.nio.file.Path

class JsonTraceStore(private val path: Path) {
    private val history = mutableListOf<TurnTrace>()

    init {
        Files.createDirectories(path.parent)
        if (Files.exists(path)) {
            runCatching {
                val loaded = JsonConfig.json.decodeFromString<List<TurnTrace>>(Files.readString(path))
                history += loaded
            }
        }
    }

    fun append(trace: TurnTrace) {
        history += trace
        Files.writeString(path, JsonConfig.json.encodeToString(history))
    }

    fun latest(): TurnTrace? = history.lastOrNull()

    fun all(): List<TurnTrace> = history.toList()
}

class JsonToolHistoryStore(private val path: Path) {
    private val history = mutableListOf<ToolCallRecord>()

    init {
        Files.createDirectories(path.parent)
        if (Files.exists(path)) {
            runCatching {
                val loaded = JsonConfig.json.decodeFromString<List<ToolCallRecord>>(Files.readString(path))
                history += loaded
            }
        }
    }

    fun append(record: ToolCallRecord) {
        history += record
        Files.writeString(path, JsonConfig.json.encodeToString(history))
    }
}

interface JobStore {
    suspend fun save(records: List<JsonObject>)
    suspend fun load(): List<JsonObject>
}

class JsonJobStore(private val path: Path) : JobStore {
    override suspend fun save(records: List<JsonObject>) {
        Files.createDirectories(path.parent)
        Files.writeString(path, JsonConfig.json.encodeToString(records))
    }

    override suspend fun load(): List<JsonObject> {
        if (!Files.exists(path)) return emptyList()
        val raw = Files.readString(path)
        return JsonConfig.json.decodeFromString(raw)
    }
}
