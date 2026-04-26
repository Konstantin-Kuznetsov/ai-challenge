package week03.shared.agent

import week03.shared.model.InvariantItem
import week03.shared.model.InvariantValidationPhases
import week03.shared.model.InvariantViolationDetail
import week03.shared.storage.InvariantsStore

sealed class InvariantCheckResult {
    data object Pass : InvariantCheckResult()

    data class Fail(val violations: List<InvariantViolationDetail>) : InvariantCheckResult()
}

/**
 * Validates (1) the payload prepared for the LLM — user text + that invariants are present in the
 * merged system prompt; (2) the model output against the same invariant definitions.
 */
interface InvariantTurnGuard {
    fun validateLlmRequest(userMessage: String, mergedSystemPrompt: String): InvariantCheckResult

    fun validateLlmResponse(assistantReply: String): InvariantCheckResult
}

object NoInvariantTurnGuard : InvariantTurnGuard {
    override fun validateLlmRequest(userMessage: String, mergedSystemPrompt: String): InvariantCheckResult =
        InvariantCheckResult.Pass

    override fun validateLlmResponse(assistantReply: String): InvariantCheckResult = InvariantCheckResult.Pass
}

class InvariantsStoreTurnGuard(
    private val store: InvariantsStore,
) : InvariantTurnGuard {

    override fun validateLlmRequest(userMessage: String, mergedSystemPrompt: String): InvariantCheckResult {
        val items = store.load().items.filter { it.enabled }
        if (items.isEmpty()) {
            return InvariantCheckResult.Pass
        }
        val violations = mutableListOf<InvariantViolationDetail>()

        if (!mergedSystemPrompt.contains("## Non-negotiable invariants")) {
            violations.add(
                InvariantViolationDetail(
                    phase = InvariantValidationPhases.REQUEST_INJECTION,
                    invariantId = null,
                    category = null,
                    ruleSummary = "Секция «## Non-negotiable invariants» не найдена в system prompt — инжект не подтверждён.",
                    matchedTrigger = null,
                ),
            )
        }
        for (inv in items) {
            if (!mergedSystemPrompt.contains(inv.id, ignoreCase = false)) {
                violations.add(
                    InvariantViolationDetail(
                        phase = InvariantValidationPhases.REQUEST_INJECTION,
                        invariantId = inv.id,
                        category = inv.category,
                        ruleSummary = "Идентификатор правила не найден в system prompt (инжект пропущен).",
                        matchedTrigger = null,
                    ),
                )
            }
        }

        for (inv in items) {
            for (t in effectiveQueryTriggers(inv)) {
                if (t.isBlank()) continue
                if (userMessage.contains(t, ignoreCase = true)) {
                    violations.add(
                        InvariantViolationDetail(
                            phase = InvariantValidationPhases.REQUEST_QUERY,
                            invariantId = inv.id,
                            category = inv.category,
                            ruleSummary = inv.text.trim(),
                            matchedTrigger = t,
                        ),
                    )
                }
            }
        }

        return if (violations.isEmpty()) InvariantCheckResult.Pass else InvariantCheckResult.Fail(violations)
    }

    override fun validateLlmResponse(assistantReply: String): InvariantCheckResult {
        val items = store.load().items.filter { it.enabled }
        if (items.isEmpty()) {
            return InvariantCheckResult.Pass
        }
        val violations = mutableListOf<InvariantViolationDetail>()
        for (inv in items) {
            for (t in effectiveResponseTriggers(inv)) {
                if (t.isBlank()) continue
                if (assistantReply.contains(t, ignoreCase = true)) {
                    violations.add(
                        InvariantViolationDetail(
                            phase = InvariantValidationPhases.RESPONSE_TEXT,
                            invariantId = inv.id,
                            category = inv.category,
                            ruleSummary = inv.text.trim(),
                            matchedTrigger = t,
                        ),
                    )
                }
            }
        }
        return if (violations.isEmpty()) InvariantCheckResult.Pass else InvariantCheckResult.Fail(violations)
    }

    private fun effectiveQueryTriggers(inv: InvariantItem): List<String> =
        when {
            inv.queryTriggers.isNotEmpty() -> inv.queryTriggers
            inv.conflictTriggers.isNotEmpty() -> inv.conflictTriggers
            else -> emptyList()
        }

    private fun effectiveResponseTriggers(inv: InvariantItem): List<String> =
        when {
            inv.responseTriggers.isNotEmpty() -> inv.responseTriggers
            inv.conflictTriggers.isNotEmpty() -> inv.conflictTriggers
            else -> emptyList()
        }
}
