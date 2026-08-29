package com.vaniflow.app.engine.ai

/**
 * Detects when the AI emits a response that is identical to, or nearly identical
 * to, a recent reply — the exact failure reported in Milestone 16 (same stored
 * sentence for different questions).
 *
 * Phase 7: tracks recent assistant responses per session. If a candidate is a
 * repeat, the router regenerates once with a stronger instruction; if it still
 * repeats, it falls back to a context-aware message instead of emitting the
 * duplicated canned text.
 */
object RepetitionGuard {

    private val recentResponses = ArrayDeque<String>(6)

    fun reset() = recentResponses.clear()

    fun record(response: String) {
        val trimmed = response.trim()
        if (trimmed.isBlank()) return
        recentResponses.addLast(trimmed)
        while (recentResponses.size > 6) recentResponses.removeFirst()
    }

    /**
     * True if [candidate] is identical to, or highly similar to, any recently
     * recorded response or any entry in [extraContext] (e.g. the immediately
     * preceding turn supplied by the caller).
     */
    fun isRepetition(candidate: String, extraContext: List<String> = emptyList()): Boolean {
        val norm = normalize(candidate)
        if (norm.isBlank()) return false
        // Short affirmations ("Yes.", "Absolutely.", "I agree.") are legitimate and
        // must never be flagged as a repetition merely because an earlier turn used
        // the same short phrase. Only compare substantive responses.
        if (norm.split(' ').size <= MIN_WORDS_FOR_REPETITION) return false
        val pool = recentResponses.toList() + extraContext.map { it.trim() }.filter { it.isNotBlank() }
        return pool.any { similarity(norm, normalize(it)) >= SIMILARITY_THRESHOLD }
    }

    private fun normalize(text: String): String =
        text.lowercase()
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun similarity(a: String, b: String): Double {
        if (a == b) return 1.0
        val setA = a.split(' ').toSet()
        val setB = b.split(' ').toSet()
        if (setA.isEmpty() || setB.isEmpty()) return 0.0
        val intersection = setA.intersect(setB).size.toDouble()
        val union = setA.union(setB).size.toDouble()
        return intersection / union
    }

    private const val SIMILARITY_THRESHOLD = 0.8
    private const val MIN_WORDS_FOR_REPETITION = 2
}
