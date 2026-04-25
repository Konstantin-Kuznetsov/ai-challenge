package week03.shared.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class ChatMessage(
    val role: String,
    val content: String,
)

@Serializable
data class AgentSettings(
    val systemPrompt: String,
    val temperature: Double = 0.3,
    val maxTokens: Int = 600,
)

@Serializable
data class AgentReply(
    val text: String,
    val inputTokens: Int? = null,
    val outputTokens: Int? = null,
    val provider: String = "stub",
)

@Serializable
enum class MemoryLayer {
    SHORT_TERM,
    WORKING,
    LONG_TERM,
}

@Serializable
data class ShortTermState(
    val messages: List<ChatMessage> = emptyList(),
)

@Serializable
data class WorkingMemoryState(
    val goal: String? = null,
    val constraints: List<String> = emptyList(),
    val decisions: List<String> = emptyList(),
    val openQuestions: List<String> = emptyList(),
    val notes: List<String> = emptyList(),
)

@Serializable
data class LongTermMemoryState(
    val activeProfileId: String? = null,
    val profile: Map<String, String> = emptyMap(),
    val knowledge: List<String> = emptyList(),
    val stableDecisions: List<String> = emptyList(),
    val updatedAtEpochMs: Long = 0L,
)

@Serializable
data class AgentProfile(
    val id: String,
    val title: String,
    val specialization: String,
    val roleSummary: String,
    val baselineRequirements: List<String>,
    val styleRules: List<String>,
)

@Serializable
data class MemoryWriteCandidate(
    val layer: MemoryLayer,
    val key: String? = null,
    val value: String,
    val reason: String,
    val confidence: Double,
)

@Serializable
data class MemoryWriteDecision(
    val candidates: List<MemoryWriteCandidate> = emptyList(),
)

@Serializable
data class MemorySnapshot(
    val shortTerm: ShortTermState,
    val working: WorkingMemoryState,
    val longTerm: LongTermMemoryState,
)

@Serializable
data class MemoryContextBundle(
    val shortTermMessagesForPrompt: List<ChatMessage>,
    val systemContextBlocks: List<String>,
    val layersUsed: List<MemoryLayer>,
    val shortHits: Int,
    val workingHits: Int,
    val longHits: Int,
)

@Serializable
data class MemoryMetrics(
    val layersUsed: List<MemoryLayer>,
    val shortHits: Int,
    val workingHits: Int,
    val longHits: Int,
    val writesShort: Int,
    val writesWorking: Int,
    val writesLong: Int,
    val classifierConfidenceAvg: Double?,
)

@Serializable
data class AgentTurnResult(
    val reply: AgentReply,
    val memorySnapshot: MemorySnapshot,
    val memoryMetrics: MemoryMetrics,
)

@Serializable
data class MemoryTestScenario(
    val id: String,
    val title: String,
    val purpose: String,
    val steps: List<String>,
    val expected: String,
)

@Serializable
data class MetricsPoint(
    val turnIndex: Int,
    val provider: String,
    val inputTokens: Int,
    val outputTokens: Int,
    @SerialName("memory_layers_used")
    val memoryLayersUsed: List<MemoryLayer>,
    @SerialName("short_hits")
    val shortHits: Int,
    @SerialName("working_hits")
    val workingHits: Int,
    @SerialName("long_hits")
    val longHits: Int,
    @SerialName("writes_short")
    val writesShort: Int,
    @SerialName("writes_working")
    val writesWorking: Int,
    @SerialName("writes_long")
    val writesLong: Int,
    @SerialName("classifier_confidence_avg")
    val classifierConfidenceAvg: Double?,
)
