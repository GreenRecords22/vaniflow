package com.vaniflow.app.engine.learning.tutor

import com.vaniflow.app.engine.learning.tutor.model.MasteryState
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MasteryEngine @Inject constructor() {

    fun onMistake(
        conceptId: String,
        category: EnglishErrorCategory,
        current: MasteryState?,
        nowEpochMs: Long = System.currentTimeMillis()
    ): MasteryState {
        val existing = current ?: MasteryState(
            conceptId = conceptId,
            category = category,
            masteryScore = 20,
            attemptCount = 0,
            successCount = 0,
            failureCount = 0,
            consecutiveSuccesses = 0,
            consecutiveFailures = 0,
            lastPracticedEpochMs = nowEpochMs,
            lastSuccessEpochMs = 0L,
            practicePriority = 80
        )

        val newFailures = existing.failureCount + 1
        val newConsecutiveFailures = existing.consecutiveFailures + 1
        // Score penalty: -12 points, floored at 0
        val newScore = (existing.masteryScore - 12).coerceAtLeast(0)
        val newPriority = calculatePriority(newScore, newConsecutiveFailures, 0)

        return existing.copy(
            attemptCount = existing.attemptCount + 1,
            failureCount = newFailures,
            consecutiveFailures = newConsecutiveFailures,
            consecutiveSuccesses = 0,
            masteryScore = newScore,
            lastPracticedEpochMs = nowEpochMs,
            practicePriority = newPriority
        )
    }

    fun onSuccessfulRetry(
        conceptId: String,
        category: EnglishErrorCategory,
        current: MasteryState?,
        nowEpochMs: Long = System.currentTimeMillis()
    ): MasteryState {
        val existing = current ?: MasteryState(
            conceptId = conceptId,
            category = category,
            masteryScore = 20,
            attemptCount = 1,
            successCount = 0,
            failureCount = 1,
            consecutiveSuccesses = 0,
            consecutiveFailures = 1,
            lastPracticedEpochMs = nowEpochMs,
            lastSuccessEpochMs = 0L,
            practicePriority = 80
        )

        val newSuccesses = existing.successCount + 1
        val newConsecutiveSuccesses = existing.consecutiveSuccesses + 1
        // Successful retry gain: +15 points, capped at 100
        val newScore = (existing.masteryScore + 15).coerceAtMost(100)
        val newPriority = calculatePriority(newScore, 0, newConsecutiveSuccesses)

        return existing.copy(
            attemptCount = existing.attemptCount + 1,
            successCount = newSuccesses,
            consecutiveSuccesses = newConsecutiveSuccesses,
            consecutiveFailures = 0,
            masteryScore = newScore,
            lastPracticedEpochMs = nowEpochMs,
            lastSuccessEpochMs = nowEpochMs,
            practicePriority = newPriority
        )
    }

    fun onNaturalCorrectUsage(
        conceptId: String,
        category: EnglishErrorCategory,
        current: MasteryState?,
        nowEpochMs: Long = System.currentTimeMillis()
    ): MasteryState {
        val existing = current ?: MasteryState(
            conceptId = conceptId,
            category = category,
            masteryScore = 50,
            attemptCount = 0,
            successCount = 0,
            failureCount = 0,
            consecutiveSuccesses = 0,
            consecutiveFailures = 0,
            lastPracticedEpochMs = nowEpochMs,
            lastSuccessEpochMs = nowEpochMs,
            practicePriority = 40
        )

        val newSuccesses = existing.successCount + 1
        val newConsecutiveSuccesses = existing.consecutiveSuccesses + 1
        // Natural correct speaking boost: +8 points
        val newScore = (existing.masteryScore + 8).coerceAtMost(100)
        val newPriority = calculatePriority(newScore, 0, newConsecutiveSuccesses)

        return existing.copy(
            attemptCount = existing.attemptCount + 1,
            successCount = newSuccesses,
            consecutiveSuccesses = newConsecutiveSuccesses,
            consecutiveFailures = 0,
            masteryScore = newScore,
            lastPracticedEpochMs = nowEpochMs,
            lastSuccessEpochMs = nowEpochMs,
            practicePriority = newPriority
        )
    }

    fun applyTimeDecay(
        mastery: MasteryState,
        nowEpochMs: Long = System.currentTimeMillis()
    ): MasteryState {
        if (mastery.lastPracticedEpochMs <= 0L) return mastery
        val daysElapsed = ((nowEpochMs - mastery.lastPracticedEpochMs) / (1000L * 60 * 60 * 24)).toInt()
        if (daysElapsed < 5) return mastery // Grace period of 5 days without decay

        // Mild spaced memory decay: 1 point per 3 days elapsed beyond 5 days, max 20 point decay
        val decayPoints = ((daysElapsed - 5) / 3).coerceIn(0, 20)
        if (decayPoints <= 0) return mastery

        val newScore = (mastery.masteryScore - decayPoints).coerceAtLeast(10)
        val newPriority = calculatePriority(newScore, mastery.consecutiveFailures, mastery.consecutiveSuccesses)

        return mastery.copy(
            masteryScore = newScore,
            practicePriority = newPriority
        )
    }

    fun calculatePriority(
        masteryScore: Int,
        consecutiveFailures: Int,
        consecutiveSuccesses: Int
    ): Int {
        // High priority = low mastery score + failure penalty
        var priority = (100 - masteryScore) + (consecutiveFailures * 10) - (consecutiveSuccesses * 5)
        return priority.coerceIn(0, 100)
    }
}
