package week03.shared.agent

import week03.shared.model.AgentProfile
import week03.shared.model.LongTermMemoryState
import week03.shared.model.MemoryContextBundle
import week03.shared.model.MemoryLayer
import week03.shared.model.MemoryMetrics
import week03.shared.model.MemorySnapshot
import week03.shared.model.MemoryWriteCandidate
import week03.shared.model.MemoryWriteDecision
import week03.shared.model.ShortTermState
import week03.shared.model.WorkingMemoryState
import week03.shared.storage.LongTermStore
import week03.shared.storage.ShortTermStore
import week03.shared.storage.WorkingMemoryStore

class MemoryLayerService(
    private val shortTermStore: ShortTermStore,
    private val workingMemoryStore: WorkingMemoryStore,
    private val longTermStore: LongTermStore,
    private val availableProfiles: Map<String, AgentProfile> = AgentProfiles.byId,
    private val defaultProfileId: String = AgentProfiles.defaultProfileId,
    private val classifierConfidenceThreshold: Double = 0.60,
) {
    init {
        ensureDefaultProfileSelected()
    }

    fun snapshot(): MemorySnapshot = MemorySnapshot(
        shortTerm = shortTermStore.load(),
        working = workingMemoryStore.load(),
        longTerm = longTermStore.load(),
    )

    fun buildContext(snapshot: MemorySnapshot = snapshot()): MemoryContextBundle {
        val layersUsed = mutableListOf<MemoryLayer>()
        val blocks = mutableListOf<String>()
        var shortHits = 0
        var workingHits = 0
        var longHits = 0

        if (snapshot.shortTerm.messages.isNotEmpty()) {
            layersUsed.add(MemoryLayer.SHORT_TERM)
            shortHits = snapshot.shortTerm.messages.size
        }
        toWorkingBlock(snapshot.working)?.let {
            layersUsed.add(MemoryLayer.WORKING)
            blocks.add(it)
            workingHits = 1
        }
        toLongTermBlock(snapshot.longTerm)?.let {
            layersUsed.add(MemoryLayer.LONG_TERM)
            blocks.add(it)
            longHits = 1
        }

        return MemoryContextBundle(
            shortTermMessagesForPrompt = snapshot.shortTerm.messages,
            systemContextBlocks = blocks,
            layersUsed = layersUsed,
            shortHits = shortHits,
            workingHits = workingHits,
            longHits = longHits,
        )
    }

    fun persistTurn(
        userText: String,
        assistantText: String,
        classifierDecision: MemoryWriteDecision,
    ): MemoryMetrics {
        val writesShort = shortTermStore.appendUserAssistant(userText = userText, assistantText = assistantText)
        val filtered = filterCandidates(classifierDecision.candidates)
        val writesWorking = workingMemoryStore.apply(filtered)
        val writesLong = 0
        val nextSnapshot = snapshot()
        val nextContext = buildContext(nextSnapshot)

        val confidenceAvg = filtered
            .map { it.confidence }
            .takeIf { it.isNotEmpty() }
            ?.average()

        return MemoryMetrics(
            layersUsed = nextContext.layersUsed,
            shortHits = nextContext.shortHits,
            workingHits = nextContext.workingHits,
            longHits = nextContext.longHits,
            writesShort = writesShort,
            writesWorking = writesWorking,
            writesLong = writesLong,
            classifierConfidenceAvg = confidenceAvg,
        )
    }

    fun clearShortTerm() = shortTermStore.clear()
    fun clearWorkingMemory() = workingMemoryStore.clear()
    fun clearLongTerm() {
        longTermStore.clear()
        ensureDefaultProfileSelected()
    }

    fun listProfiles(): List<AgentProfile> = AgentProfiles.all.filter { availableProfiles.containsKey(it.id) }

    fun selectProfile(profileId: String): Boolean {
        val profile = availableProfiles[profileId] ?: return false
        longTermStore.setProfile(profile)
        return true
    }

    private fun filterCandidates(candidates: List<MemoryWriteCandidate>): List<MemoryWriteCandidate> {
        return candidates.filter {
            it.layer == MemoryLayer.WORKING &&
                it.confidence >= classifierConfidenceThreshold &&
                it.value.isNotBlank() &&
                it.reason.isNotBlank()
        }
    }

    private fun ensureDefaultProfileSelected() {
        val current = longTermStore.load()
        if (!current.activeProfileId.isNullOrBlank()) {
            return
        }
        availableProfiles[defaultProfileId]?.let { longTermStore.setProfile(it) }
    }

    private fun toWorkingBlock(working: WorkingMemoryState): String? {
        val lines = mutableListOf<String>()
        working.goal?.let { lines.add("- goal: $it") }
        if (working.constraints.isNotEmpty()) {
            lines.add("- constraints: ${working.constraints.joinToString("; ")}")
        }
        if (working.decisions.isNotEmpty()) {
            lines.add("- decisions: ${working.decisions.joinToString("; ")}")
        }
        if (working.openQuestions.isNotEmpty()) {
            lines.add("- open_questions: ${working.openQuestions.joinToString("; ")}")
        }
        if (working.notes.isNotEmpty()) {
            lines.add("- notes: ${working.notes.joinToString("; ")}")
        }
        if (lines.isEmpty()) {
            return null
        }
        return "Working memory (current task):\n" + lines.joinToString("\n")
    }

    private fun toLongTermBlock(longTerm: LongTermMemoryState): String? {
        val lines = mutableListOf<String>()
        if (longTerm.profile.isNotEmpty()) {
            lines.add("- profile: " + longTerm.profile.entries.joinToString("; ") { "${it.key}=${it.value}" })
        }
        if (longTerm.knowledge.isNotEmpty()) {
            lines.add("- knowledge: ${longTerm.knowledge.joinToString("; ")}")
        }
        if (longTerm.stableDecisions.isNotEmpty()) {
            lines.add("- stable_decisions: ${longTerm.stableDecisions.joinToString("; ")}")
        }
        if (lines.isEmpty()) {
            return null
        }
        return "Long-term memory (cross-task profile and knowledge):\n" + lines.joinToString("\n")
    }
}
