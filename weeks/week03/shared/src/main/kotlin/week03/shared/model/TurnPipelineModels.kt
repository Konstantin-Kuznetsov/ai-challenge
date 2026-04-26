package week03.shared.model

import kotlinx.serialization.Serializable

/** Fine-grained phase for a single violated rule (shown in UI). */
object InvariantValidationPhases {
    const val REQUEST_INJECTION = "REQUEST_INJECTION"
    const val REQUEST_QUERY = "REQUEST_QUERY"
    const val RESPONSE_TEXT = "RESPONSE_TEXT"
}

@Serializable
data class InvariantViolationDetail(
    val phase: String,
    val invariantId: String? = null,
    val category: String? = null,
    val ruleSummary: String,
    val matchedTrigger: String? = null,
)

@Serializable
data class TurnValidationInfo(
    val failed: Boolean,
    /** Umbrella phase for the whole failed gate: REQUEST_VALIDATION | RESPONSE_VALIDATION */
    val gate: String? = null,
    val violations: List<InvariantViolationDetail> = emptyList(),
)

@Serializable
data class TurnPipelineStep(
    val id: String,
    val label: String,
    /** passed | failed | skipped | pending */
    val status: String,
    val detail: String? = null,
    /** Previous step id for UI graph (allowed transition from -> to). */
    val fromStepId: String? = null,
)
