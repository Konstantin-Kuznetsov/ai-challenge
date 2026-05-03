package week04.task3.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import java.time.Instant

@Serializable
data class ToolCallPayload(
    val name: String,
    val arguments: Map<String, JsonElement> = emptyMap(),
)

@Serializable
data class JobState(
    val enabled: Boolean = true,
    val intervalSeconds: Long = 120,
    val lastRunAt: String? = null,
    val nextRunAt: String? = null,
    val runCount: Int = 0,
    val lastStatus: String = "idle",
    val lastError: String? = null,
    val lastDurationMs: Long? = null,
)

@Serializable
data class PriceMeasurement(
    val timestamp: String = Instant.now().toString(),
    val symbol: String,
    val currency: String,
    val price: Double,
    val change24h: Double? = null,
    val source: String = "mcp:getCoinPrice",
)

@Serializable
data class SummarySnapshot(
    val generatedAt: String = Instant.now().toString(),
    val symbol: String,
    val currency: String,
    val intervalSeconds: Long,
    val points: Int,
    val latestPrice: Double,
    val minPrice: Double,
    val maxPrice: Double,
    val avgPrice: Double,
    val changeFromFirstPct: Double? = null,
)

@Serializable
data class StatusResponse(
    val mcpConnected: Boolean,
    val jobState: JobState,
    val summary: SummarySnapshot?,
)

@Serializable
data class RunNowResponse(
    val ok: Boolean,
    val trigger: String,
    val measurement: PriceMeasurement? = null,
    val summary: SummarySnapshot? = null,
    val jobState: JobState,
    val error: String? = null,
)

@Serializable
data class SummaryResponse(
    val summary: SummarySnapshot?,
    val jobState: JobState,
)

@Serializable
data class HistoryResponse(
    val items: List<PriceMeasurement>,
)
