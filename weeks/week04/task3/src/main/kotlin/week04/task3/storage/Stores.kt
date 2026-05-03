package week04.task3.storage

import java.nio.file.Files
import java.nio.file.Path
import week04.task3.config.JsonConfig
import week04.task3.model.JobState
import week04.task3.model.PriceMeasurement
import week04.task3.model.SummarySnapshot

class JsonJobStateStore(private val path: Path) {
    init {
        Files.createDirectories(path.parent)
    }

    fun save(state: JobState) {
        Files.writeString(path, JsonConfig.json.encodeToString(state))
    }

    fun load(default: JobState): JobState {
        if (!Files.exists(path)) return default
        return runCatching {
            JsonConfig.json.decodeFromString<JobState>(Files.readString(path))
        }.getOrDefault(default)
    }
}

class JsonMeasurementStore(private val path: Path) {
    private val history = mutableListOf<PriceMeasurement>()

    init {
        Files.createDirectories(path.parent)
        if (Files.exists(path)) {
            runCatching {
                val loaded = JsonConfig.json.decodeFromString<List<PriceMeasurement>>(Files.readString(path))
                history += loaded
            }
        }
    }

    fun append(item: PriceMeasurement) {
        history += item
        Files.writeString(path, JsonConfig.json.encodeToString(history))
    }

    fun all(): List<PriceMeasurement> = history.toList()
}

class JsonSummaryStore(private val path: Path) {
    init {
        Files.createDirectories(path.parent)
    }

    fun save(summary: SummarySnapshot) {
        Files.writeString(path, JsonConfig.json.encodeToString(summary))
    }

    fun load(): SummarySnapshot? {
        if (!Files.exists(path)) return null
        return runCatching {
            JsonConfig.json.decodeFromString<SummarySnapshot>(Files.readString(path))
        }.getOrNull()
    }
}
