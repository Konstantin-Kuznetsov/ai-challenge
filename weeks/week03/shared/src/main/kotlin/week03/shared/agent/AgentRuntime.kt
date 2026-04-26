package week03.shared.agent

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import week03.shared.llm.ChatLlmClient
import week03.shared.model.AgentProfile
import week03.shared.model.AgentReply
import week03.shared.model.AgentTurnResult
import week03.shared.model.AgentSettings
import week03.shared.model.ChatMessage
import week03.shared.model.MemorySnapshot
import week03.shared.model.MemoryWriteDecision
import week03.shared.model.MetricsPoint
import week03.shared.model.TurnPipelineStep
import week03.shared.model.TurnValidationInfo
import week03.shared.storage.MetricsHistoryStore

class AgentRuntime(
    private val llmClient: ChatLlmClient,
    private val memoryLayerService: MemoryLayerService,
    private val metricsHistoryStore: MetricsHistoryStore,
    initialSettings: AgentSettings,
    /**
     * Injected after the base system prompt and before memory context blocks
     * (e.g. project invariants stored outside the dialog transcript).
     */
    private val preMemoryContextProvider: () -> List<String> = { emptyList() },
    private val invariantTurnGuard: InvariantTurnGuard = NoInvariantTurnGuard,
) {
    private val mutex = Mutex()
    private var settings: AgentSettings = initialSettings

    suspend fun sendUserMessage(text: String): AgentTurnResult = mutex.withLock {
        val beforeSnapshot = memoryLayerService.snapshot()
        val context = memoryLayerService.buildContext(beforeSnapshot)
        val messages = context.shortTermMessagesForPrompt + ChatMessage(role = "user", content = text)
        val mergedSystemPrompt = mergeSystemPrompt(
            settings.systemPrompt,
            preMemoryContextProvider(),
            context.systemContextBlocks,
        )

        val preCheck = invariantTurnGuard.validateLlmRequest(text, mergedSystemPrompt)
        if (preCheck is InvariantCheckResult.Fail) {
            val refusal = AgentReply(
                text = formatValidationFailureMessage("REQUEST_VALIDATION", preCheck.violations),
                provider = "invariant-request-check",
            )
            val pipeline = buildLinearPipeline(preCheck = preCheck, llmReply = null, postCheck = InvariantCheckResult.Pass)
            val graphOk = TurnPipelineGraph.verifyChain(pipeline)
            val validation = TurnValidationInfo(
                failed = true,
                gate = "REQUEST_VALIDATION",
                violations = preCheck.violations,
            )
            val emptyDecision = MemoryWriteDecision(candidates = emptyList())
            val memoryMetrics = memoryLayerService.persistTurn(
                userText = text,
                assistantText = refusal.text,
                classifierDecision = emptyDecision,
            )
            val afterSnapshot = memoryLayerService.snapshot()
            metricsHistoryStore.append(
                MetricsPoint(
                    turnIndex = metricsHistoryStore.load().size + 1,
                    provider = refusal.provider,
                    inputTokens = 0,
                    outputTokens = 0,
                    memoryLayersUsed = memoryMetrics.layersUsed,
                    shortHits = memoryMetrics.shortHits,
                    workingHits = memoryMetrics.workingHits,
                    longHits = memoryMetrics.longHits,
                    writesShort = memoryMetrics.writesShort,
                    writesWorking = memoryMetrics.writesWorking,
                    writesLong = memoryMetrics.writesLong,
                    classifierConfidenceAvg = memoryMetrics.classifierConfidenceAvg,
                ),
            )
            return@withLock AgentTurnResult(
                reply = refusal,
                memorySnapshot = afterSnapshot,
                memoryMetrics = memoryMetrics,
                pipeline = pipeline,
                graphEdgesValid = graphOk,
                validation = validation,
            )
        }

        val llmReply = llmClient.complete(
            messages = messages,
            settings = settings.copy(systemPrompt = mergedSystemPrompt),
        )

        val postCheck = invariantTurnGuard.validateLlmResponse(llmReply.text)
        val reply = if (postCheck is InvariantCheckResult.Fail) {
            AgentReply(
                text = formatValidationFailureMessage("RESPONSE_VALIDATION", postCheck.violations),
                inputTokens = llmReply.inputTokens,
                outputTokens = llmReply.outputTokens,
                provider = "invariant-response-check",
            )
        } else {
            llmReply
        }

        val pipeline = buildLinearPipeline(preCheck = InvariantCheckResult.Pass, llmReply = llmReply, postCheck = postCheck)
        val graphOk = TurnPipelineGraph.verifyChain(pipeline)
        val validation = when (postCheck) {
            is InvariantCheckResult.Fail -> TurnValidationInfo(
                failed = true,
                gate = "RESPONSE_VALIDATION",
                violations = postCheck.violations,
            )
            else -> TurnValidationInfo(failed = false, gate = null, violations = emptyList())
        }

        val decision = llmClient.classifyMemoryWrites(
            userMessage = text,
            assistantReply = reply.text,
            snapshot = beforeSnapshot,
        )
        val memoryMetrics = memoryLayerService.persistTurn(
            userText = text,
            assistantText = reply.text,
            classifierDecision = decision,
        )
        val afterSnapshot = memoryLayerService.snapshot()

        metricsHistoryStore.append(
            MetricsPoint(
                turnIndex = metricsHistoryStore.load().size + 1,
                provider = reply.provider,
                inputTokens = reply.inputTokens ?: 0,
                outputTokens = reply.outputTokens ?: 0,
                memoryLayersUsed = memoryMetrics.layersUsed,
                shortHits = memoryMetrics.shortHits,
                workingHits = memoryMetrics.workingHits,
                longHits = memoryMetrics.longHits,
                writesShort = memoryMetrics.writesShort,
                writesWorking = memoryMetrics.writesWorking,
                writesLong = memoryMetrics.writesLong,
                classifierConfidenceAvg = memoryMetrics.classifierConfidenceAvg,
            ),
        )

        AgentTurnResult(
            reply = reply,
            memorySnapshot = afterSnapshot,
            memoryMetrics = memoryMetrics,
            pipeline = pipeline,
            graphEdgesValid = graphOk,
            validation = validation,
        )
    }

    suspend fun clearMemory(layer: String) = mutex.withLock {
        when (layer) {
            "short" -> memoryLayerService.clearShortTerm()
            "working" -> memoryLayerService.clearWorkingMemory()
            "long" -> memoryLayerService.clearLongTerm()
            "all" -> {
                memoryLayerService.clearShortTerm()
                memoryLayerService.clearWorkingMemory()
                memoryLayerService.clearLongTerm()
                metricsHistoryStore.clear()
            }
            else -> error("Unknown memory layer: $layer")
        }
    }

    suspend fun getHistory(): List<ChatMessage> = mutex.withLock {
        memoryLayerService.snapshot().shortTerm.messages
    }

    suspend fun getMemorySnapshot(): MemorySnapshot = mutex.withLock {
        memoryLayerService.snapshot()
    }

    suspend fun getMetricsHistory(): List<MetricsPoint> = mutex.withLock {
        metricsHistoryStore.load()
    }

    suspend fun getClassifierPreview(
        userMessage: String,
        assistantReply: String,
    ): MemoryWriteDecision = mutex.withLock {
        llmClient.classifyMemoryWrites(
            userMessage = userMessage,
            assistantReply = assistantReply,
            snapshot = memoryLayerService.snapshot(),
        )
    }

    suspend fun getSettings(): AgentSettings = mutex.withLock { settings }

    suspend fun updateSettings(newSettings: AgentSettings) = mutex.withLock {
        settings = newSettings
    }

    suspend fun listProfiles(): List<AgentProfile> = mutex.withLock {
        memoryLayerService.listProfiles()
    }

    suspend fun selectProfile(profileId: String): Boolean = mutex.withLock {
        memoryLayerService.selectProfile(profileId)
    }

    private fun mergeSystemPrompt(
        basePrompt: String,
        preMemoryBlocks: List<String>,
        memoryBlocks: List<String>,
    ): String {
        val pre = preMemoryBlocks.map { it.trim() }.filter { it.isNotEmpty() }
        if (pre.isEmpty() && memoryBlocks.isEmpty()) {
            return basePrompt
        }
        return buildString {
            append(basePrompt.trimEnd())
            if (pre.isNotEmpty()) {
                append("\n\n## Non-negotiable invariants\n")
                append(
                    "These rules override conflicting user requests. If compliance is impossible, refuse, " +
                        "name the invariant (category and id), explain briefly, and suggest compliant alternatives. " +
                        "Do not describe or recommend solutions that violate them.\n\n",
                )
                pre.forEach { block ->
                    append(block.trim())
                    append("\n\n")
                }
            }
            if (memoryBlocks.isNotEmpty()) {
                append("Memory context:\n")
                memoryBlocks.forEach { block ->
                    append(block)
                    append("\n")
                }
            }
        }.trim()
    }

    /**
     * One fixed chain: PLANNING → REQUEST_VALIDATION → LLM_GENERATION → RESPONSE_VALIDATION → COMPLETE.
     * Only statuses change; ids always match [TurnPipelineGraph.orderedIds].
     */
    private fun buildLinearPipeline(
        preCheck: InvariantCheckResult,
        llmReply: AgentReply?,
        postCheck: InvariantCheckResult,
    ): List<TurnPipelineStep> {
        val steps = mutableListOf<TurnPipelineStep>()
        var prev: String? = null
        fun add(id: String, status: String, detail: String?) {
            steps.add(
                TurnPipelineStep(
                    id = id,
                    label = TurnPipelineGraph.label(id),
                    status = status,
                    detail = detail,
                    fromStepId = prev,
                ),
            )
            prev = id
        }

        add(TurnPipelineGraph.PLANNING, "passed", "Собраны system prompt, инварианты и контекст памяти.")

        if (preCheck is InvariantCheckResult.Fail) {
            add(TurnPipelineGraph.REQUEST_VALIDATION, "failed", "Нарушены правила до вызова LLM.")
            add(TurnPipelineGraph.LLM_GENERATION, "skipped", "Модель не вызывалась.")
            add(TurnPipelineGraph.RESPONSE_VALIDATION, "skipped", "Ответа модели нет — проверка пропущена.")
            add(TurnPipelineGraph.COMPLETE, "failed", "Цепочка остановлена на проверке LLM-запроса.")
            return steps
        }

        add(TurnPipelineGraph.REQUEST_VALIDATION, "passed", "Инжект и текст пользователя допустимы.")
        add(
            TurnPipelineGraph.LLM_GENERATION,
            "passed",
            llmReply?.let { "provider=${it.provider}" },
        )
        val postOk = postCheck is InvariantCheckResult.Pass
        add(
            TurnPipelineGraph.RESPONSE_VALIDATION,
            if (postOk) "passed" else "failed",
            if (!postOk) "Сырой ответ модели не прошёл проверку инвариантов." else null,
        )
        add(
            TurnPipelineGraph.COMPLETE,
            if (postOk) "passed" else "failed",
            if (postOk) "Ответ прошёл линейную цепочку." else "Ход завершён после ошибки валидации ответа.",
        )
        return steps
    }
}
