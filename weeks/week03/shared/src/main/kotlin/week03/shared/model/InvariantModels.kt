package week03.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class InvariantItem(
    val id: String,
    /** Short label, e.g. ARCHITECTURE, STACK, BUSINESS. */
    val category: String,
    /** Human-readable rule text; injected into system prompt. */
    val text: String,
    val enabled: Boolean = true,
    /**
     * If the **user message** contains any substring (case-insensitive), the assembled LLM request
     * is rejected before calling the model.
     */
    val queryTriggers: List<String> = emptyList(),
    /**
     * If the **assistant reply** contains any substring (case-insensitive), the reply is rejected
     * after generation (user sees a short refusal instead of the raw model text).
     */
    val responseTriggers: List<String> = emptyList(),
    /**
     * Legacy: when [queryTriggers] / [responseTriggers] are empty, these apply to **both**
     * request and response checks (backwards compatible with older JSON).
     */
    val conflictTriggers: List<String> = emptyList(),
)

@Serializable
data class InvariantsState(
    val items: List<InvariantItem> = emptyList(),
)
