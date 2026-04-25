package week03.shared.agent

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import week03.shared.llm.ChatLlmClient
import week03.shared.model.AgentProfile
import week03.shared.model.AgentTurnResult
import week03.shared.model.AgentSettings
import week03.shared.model.ChatMessage
import week03.shared.model.MemorySnapshot
import week03.shared.model.MemoryWriteDecision
import week03.shared.model.MetricsPoint
import week03.shared.storage.MetricsHistoryStore

class AgentRuntime(
    private val llmClient: ChatLlmClient,
    private val memoryLayerService: MemoryLayerService,
    private val metricsHistoryStore: MetricsHistoryStore,
    initialSettings: AgentSettings,
) {
    private val mutex = Mutex()
    private var settings: AgentSettings = initialSettings

    suspend fun sendUserMessage(text: String): AgentTurnResult = mutex.withLock {
        val beforeSnapshot = memoryLayerService.snapshot()
        val context = memoryLayerService.buildContext(beforeSnapshot)
        val messages = context.shortTermMessagesForPrompt + ChatMessage(role = "user", content = text)
        val mergedSystemPrompt = mergeSystemPrompt(settings.systemPrompt, context.systemContextBlocks)

        val reply = llmClient.complete(
            messages = messages,
            settings = settings.copy(systemPrompt = mergedSystemPrompt),
        )

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

    private fun mergeSystemPrompt(basePrompt: String, blocks: List<String>): String {
        if (blocks.isEmpty()) {
            return basePrompt
        }
        return buildString {
            append(basePrompt)
            append("\n\nMemory context:\n")
            blocks.forEach { block ->
                append(block)
                append("\n")
            }
        }.trim()
    }
}
