package week03.shared.agent

import week03.shared.model.TurnPipelineStep

/**
 * Linear FSM for one chat turn (task4 / invariant path). UI must only connect consecutive ids
 * in this order — no other transitions are emitted by the server.
 */
object TurnPipelineGraph {
    const val PLANNING = "PLANNING"
    const val REQUEST_VALIDATION = "REQUEST_VALIDATION"
    const val LLM_GENERATION = "LLM_GENERATION"
    const val RESPONSE_VALIDATION = "RESPONSE_VALIDATION"
    const val COMPLETE = "COMPLETE"

    val orderedIds: List<String> = listOf(
        PLANNING,
        REQUEST_VALIDATION,
        LLM_GENERATION,
        RESPONSE_VALIDATION,
        COMPLETE,
    )

    private val labelsRu: Map<String, String> = mapOf(
        PLANNING to "Планирование",
        REQUEST_VALIDATION to "Проверка LLM-запроса",
        LLM_GENERATION to "Генерация (LLM)",
        RESPONSE_VALIDATION to "Проверка ответа",
        COMPLETE to "Итог хода",
    )

    fun label(id: String): String = labelsRu[id] ?: id

    /** Allowed directed edges in the canonical chain. */
    val allowedEdges: Set<Pair<String, String>> = orderedIds.zipWithNext().toSet()

    fun verifyChain(steps: List<TurnPipelineStep>): Boolean {
        if (steps.isEmpty()) return false
        if (steps.map { it.id } != orderedIds) return false
        for (i in 0 until steps.lastIndex) {
            val from = steps[i].id
            val to = steps[i + 1].id
            if (from to to !in allowedEdges) return false
        }
        return true
    }
}
