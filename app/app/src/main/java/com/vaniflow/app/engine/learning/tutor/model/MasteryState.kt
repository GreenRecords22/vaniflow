package com.vaniflow.app.engine.learning.tutor.model

import com.vaniflow.app.engine.learning.tutor.EnglishErrorCategory

/**
 * Measurable, deterministic mastery state for a grammar/language concept.
 */
data class MasteryState(
    val conceptId: String,
    val category: EnglishErrorCategory,
    val masteryScore: Int = 0, // 0 to 100
    val attemptCount: Int = 0,
    val successCount: Int = 0,
    val failureCount: Int = 0,
    val consecutiveSuccesses: Int = 0,
    val consecutiveFailures: Int = 0,
    val lastPracticedEpochMs: Long = 0L,
    val lastSuccessEpochMs: Long = 0L,
    val practicePriority: Int = 50 // 0 (low) to 100 (highest practice priority)
) {
    val isMastered: Boolean get() = masteryScore >= 80 && consecutiveSuccesses >= 2
    val isImproving: Boolean get() = masteryScore in 40..79
    val needsPractice: Boolean get() = masteryScore < 40 || consecutiveFailures >= 2
}
