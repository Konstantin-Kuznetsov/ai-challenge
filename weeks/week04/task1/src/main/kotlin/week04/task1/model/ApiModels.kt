package week04.task1.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import java.time.Instant

@Serializable
data class ToolInfo(
    val name: String,
    val description: String,
    val inputSchema: String,
)

@Serializable
data class ToolsResponse(
    val tools: List<ToolInfo>,
    val trace: TurnTrace,
)

@Serializable
data class ToolCallResponse(
    val result: String,
    val trace: TurnTrace,
)

@Serializable
data class ChatResponse(
    val reply: String,
    val mcp_used: Boolean,
    val decision_by: String,
    val decision_reason: String,
    val available_tools: List<String> = emptyList(),
    val tool_used: String? = null,
    val schema_validation: SchemaValidationResult? = null,
    val normalized_tool_result: NormalizedToolResult? = null,
    val tool_result: String? = null,
    val quote_card: QuoteCard? = null,
    val trace: TurnTrace,
)

@Serializable
data class SchemaValidationResult(
    val valid: Boolean,
    val errors: List<String> = emptyList(),
)

@Serializable
data class NormalizedToolResult(
    val summary: String,
    val raw: String,
)

@Serializable
data class QuoteCard(
    val coinId: String,
    val coinName: String? = null,
    val symbol: String? = null,
    val currency: String,
    val price: Double,
    val imageUrl: String? = null,
    val change24h: Double? = null,
)

@Serializable
data class LatestTraceResponse(
    val trace: TurnTrace?,
)

@Serializable
data class StatusResponse(
    val connected: Boolean,
    val server: String,
)

@Serializable
data class ToolCallPayload(
    val name: String,
    val arguments: Map<String, JsonElement> = emptyMap(),
)

@Serializable
data class ChatPayload(
    val message: String,
)

@Serializable
data class TraceStep(
    val name: String,
    val latencyMs: Long,
    val status: String,
)

@Serializable
data class TokenUsage(
    val inputTokens: Int,
    val outputTokens: Int,
    val totalTokens: Int,
)

@Serializable
data class TurnTrace(
    val requestId: String,
    val timestamp: String = Instant.now().toString(),
    val steps: MutableList<TraceStep> = mutableListOf(),
    var llmDecision: TokenUsage? = null,
    var llmSynthesis: TokenUsage? = null,
    var llm: TokenUsage? = null,
)

@Serializable
data class ToolCallRecord(
    val timestamp: String,
    val tool: String,
    val arguments: Map<String, JsonElement>,
    val response: String,
)
