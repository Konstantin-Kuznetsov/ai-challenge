package week03.shared.agent

import week03.shared.model.InvariantValidationPhases
import week03.shared.model.InvariantViolationDetail

fun formatValidationFailureMessage(gate: String, violations: List<InvariantViolationDetail>): String {
    if (violations.isEmpty()) {
        return "### Ошибка валидации\nВоротник: ${gateHumanRu(gate)}\n"
    }
    return buildString {
        appendLine("### Ошибка валидации")
        appendLine("Воротник: ${gateHumanRu(gate)}")
        appendLine()
        violations.forEachIndexed { idx, v ->
            append("${idx + 1}. ")
            append(phaseHumanRu(v.phase))
            append(" — ")
            if (v.category != null) append("[").append(v.category).append("] ")
            if (v.invariantId != null) append("`").append(v.invariantId).append("` ")
            appendLine(v.ruleSummary.trim())
            if (!v.matchedTrigger.isNullOrBlank()) {
                appendLine("   Совпадение в тексте: «${v.matchedTrigger}»")
            }
            appendLine()
        }
    }.trim()
}

private fun gateHumanRu(gate: String): String = when (gate) {
    "REQUEST_VALIDATION" -> "до вызова LLM (проверка запроса: инжект инвариантов + текст пользователя)"
    "RESPONSE_VALIDATION" -> "после ответа модели (проверка текста ответа)"
    else -> gate
}

private fun phaseHumanRu(phase: String): String = when (phase) {
    InvariantValidationPhases.REQUEST_INJECTION -> "Инжект в prompt"
    InvariantValidationPhases.REQUEST_QUERY -> "Текст пользователя"
    InvariantValidationPhases.RESPONSE_TEXT -> "Текст ответа модели"
    else -> phase
}
