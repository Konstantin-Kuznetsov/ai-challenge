package week04.task3.service

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import week04.task3.config.JsonConfig
import week04.task3.mcp.McpClientBridge
import week04.task3.model.JobState
import week04.task3.model.PriceMeasurement
import week04.task3.model.RunNowResponse
import week04.task3.model.SummarySnapshot
import week04.task3.storage.JsonJobStateStore
import week04.task3.storage.JsonMeasurementStore
import week04.task3.storage.JsonSummaryStore
import java.time.Duration
import java.time.Instant

class SchedulerService(
    private val mcpClient: McpClientBridge,
    private val jobStateStore: JsonJobStateStore,
    private val measurementStore: JsonMeasurementStore,
    private val summaryStore: JsonSummaryStore,
    private val symbol: String = "BTC",
    private val currency: String = "usd",
    private val windowHours: Long = 24,
) {
    private companion object {
        const val FIXED_INTERVAL_SECONDS: Long = 20
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val runMutex = Mutex()
    private val defaultState = JobState(
        enabled = true,
        intervalSeconds = FIXED_INTERVAL_SECONDS,
        nextRunAt = Instant.now().toString(),
        lastStatus = "idle",
    )

    @Volatile
    private var state: JobState = sanitizeState(jobStateStore.load(defaultState))

    @Volatile
    private var loopJob: Job? = null

    fun start() {
        if (loopJob != null) return
        jobStateStore.save(state)
        loopJob = scope.launch {
            while (isActive) {
                val current = state
                val due = current.enabled && isDue(current.nextRunAt)
                if (due) {
                    runPipeline("periodic")
                }
                delay(1000)
            }
        }
    }

    suspend fun stop() {
        loopJob?.cancel()
        loopJob = null
    }

    fun state(): JobState = state

    fun summary(): SummarySnapshot? = summaryStore.load()

    fun history(limit: Int): List<PriceMeasurement> {
        val items = measurementStore.all()
        return if (limit > 0) items.takeLast(limit) else items
    }

    suspend fun runNow(): RunNowResponse {
        return runPipeline("manual")
    }

    private suspend fun runPipeline(trigger: String): RunNowResponse {
        return runMutex.withLock {
            val startedAt = Instant.now()
            val intervalSeconds = FIXED_INTERVAL_SECONDS
            try {
                val raw = mcpClient.callTool(
                    name = "getCoinPrice",
                    arguments = mapOf(
                        "symbol" to JsonPrimitive(symbol),
                        "vsCurrency" to JsonPrimitive(currency),
                    ),
                )
                val measurement = parseMeasurement(raw, startedAt)
                measurementStore.append(measurement)
                val summary = computeSummary(
                    measurements = measurementStore.all(),
                    intervalSeconds = intervalSeconds,
                    now = startedAt,
                )
                summaryStore.save(summary)
                val elapsedMs = Duration.between(startedAt, Instant.now()).toMillis()
                state = state.copy(
                    intervalSeconds = intervalSeconds,
                    lastRunAt = startedAt.toString(),
                    nextRunAt = startedAt.plusSeconds(intervalSeconds).toString(),
                    runCount = state.runCount + 1,
                    lastStatus = "ok",
                    lastError = null,
                    lastDurationMs = elapsedMs,
                )
                jobStateStore.save(state)
                RunNowResponse(
                    ok = true,
                    trigger = trigger,
                    measurement = measurement,
                    summary = summary,
                    jobState = state,
                )
            } catch (e: Exception) {
                val failedAt = Instant.now()
                state = state.copy(
                    intervalSeconds = intervalSeconds,
                    lastRunAt = failedAt.toString(),
                    nextRunAt = failedAt.plusSeconds(intervalSeconds).toString(),
                    lastStatus = "error",
                    lastError = e.message ?: e::class.simpleName,
                )
                jobStateStore.save(state)
                RunNowResponse(
                    ok = false,
                    trigger = trigger,
                    jobState = state,
                    error = e.message ?: "unknown error",
                )
            }
        }
    }

    private fun computeSummary(
        measurements: List<PriceMeasurement>,
        intervalSeconds: Long,
        now: Instant,
    ): SummarySnapshot {
        val from = now.minusSeconds(windowHours * 3600)
        val selected = measurements.filter {
            runCatching { Instant.parse(it.timestamp).isAfter(from) || Instant.parse(it.timestamp) == from }.getOrDefault(true)
        }
        val dataset = if (selected.isEmpty()) measurements else selected
        val prices = dataset.map { it.price }
        val latest = dataset.last()
        val firstPrice = dataset.firstOrNull()?.price
        val latestPrice = prices.last()
        val changePct = if (firstPrice != null && firstPrice != 0.0) ((latestPrice - firstPrice) / firstPrice) * 100.0 else null

        return SummarySnapshot(
            generatedAt = now.toString(),
            symbol = latest.symbol,
            currency = latest.currency,
            intervalSeconds = intervalSeconds,
            points = dataset.size,
            latestPrice = latestPrice,
            minPrice = prices.minOrNull() ?: latestPrice,
            maxPrice = prices.maxOrNull() ?: latestPrice,
            avgPrice = prices.average(),
            changeFromFirstPct = changePct,
        )
    }

    private fun parseMeasurement(raw: String, timestamp: Instant): PriceMeasurement {
        val json = JsonConfig.json.parseToJsonElement(raw).jsonObject
        val parsedSymbol = json["symbol"]?.jsonPrimitive?.content ?: symbol
        val parsedCurrency = json["vsCurrency"]?.jsonPrimitive?.content ?: currency
        val price = json["price"]?.jsonPrimitive?.doubleOrNull
            ?: throw IllegalStateException("MCP tool result does not contain price")
        val change24h = json["change24h"]?.jsonPrimitive?.doubleOrNull
        return PriceMeasurement(
            timestamp = timestamp.toString(),
            symbol = parsedSymbol.uppercase(),
            currency = parsedCurrency.uppercase(),
            price = price,
            change24h = change24h,
        )
    }

    private fun isDue(nextRunAt: String?): Boolean {
        if (nextRunAt.isNullOrBlank()) return true
        val next = runCatching { Instant.parse(nextRunAt) }.getOrNull() ?: return true
        return !next.isAfter(Instant.now())
    }

    private fun sanitizeState(raw: JobState): JobState {
        val now = Instant.now()
        val normalizedNextRun = now.plusSeconds(FIXED_INTERVAL_SECONDS).toString()
        return raw.copy(
            intervalSeconds = FIXED_INTERVAL_SECONDS,
            nextRunAt = normalizedNextRun,
        )
    }
}
