package week03.shared.storage

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import week03.shared.model.MetricsPoint

class MetricsHistoryStore(private val path: Path) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    fun load(): List<MetricsPoint> {
        if (!path.exists()) {
            return emptyList()
        }
        return try {
            val raw = Files.readString(path)
            if (raw.isBlank()) {
                emptyList()
            } else {
                json.decodeFromString<List<MetricsPoint>>(raw)
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun append(point: MetricsPoint) {
        val next = load().toMutableList()
        next.add(point)
        save(next)
    }

    fun clear() = save(emptyList())

    private fun save(points: List<MetricsPoint>) {
        Files.createDirectories(path.parent)
        Files.writeString(path, json.encodeToString(points))
    }
}
