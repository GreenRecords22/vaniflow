package com.vaniflow.app.engine.learning.tutor.model

import java.util.concurrent.ConcurrentHashMap

/**
 * State and cooldowns for intelligent correction decisions.
 */
data class CorrectionPolicyState(
    val conceptCooldowns: MutableMap<String, Long> = ConcurrentHashMap(), // conceptId -> cooldownExpiryEpochMs
    var consecutiveErrorsCount: Int = 0,
    var consecutiveSuccessfulTurns: Int = 0,
    var isStruggleBackoffActive: Boolean = false,
    var totalSessionCorrections: Int = 0
) {
    fun isConceptInCooldown(conceptId: String, nowEpochMs: Long = System.currentTimeMillis()): Boolean {
        val expiry = conceptCooldowns[conceptId] ?: return false
        return nowEpochMs < expiry
    }

    fun setConceptCooldown(conceptId: String, durationMs: Long = 60_000L, nowEpochMs: Long = System.currentTimeMillis()) {
        conceptCooldowns[conceptId] = nowEpochMs + durationMs
    }

    fun recordError(conceptId: String, cooldownDurationMs: Long = 60_000L) {
        consecutiveErrorsCount++
        consecutiveSuccessfulTurns = 0
        totalSessionCorrections++
        setConceptCooldown(conceptId, cooldownDurationMs)
        if (consecutiveErrorsCount >= 3) {
            isStruggleBackoffActive = true
        }
    }

    fun recordCleanTurn() {
        consecutiveSuccessfulTurns++
        if (consecutiveSuccessfulTurns >= 2) {
            consecutiveErrorsCount = 0
            isStruggleBackoffActive = false
        }
    }
}
