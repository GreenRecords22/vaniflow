package com.vaniflow.app.engine.learning.tutor

import com.vaniflow.app.engine.learning.tutor.model.LearningEvent
import com.vaniflow.app.engine.learning.tutor.model.LearningEventType
import com.vaniflow.app.engine.learning.tutor.model.MasteryState
import com.vaniflow.app.engine.learning.tutor.model.SessionLearningSummary
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProgressCalculationEngine @Inject constructor() {

    fun estimateCefrLevel(
        profile: LearnerProfile,
        masteryList: List<MasteryState>
    ): EstimatedLevel {
        if (profile.totalUtterances < 10) {
            return profile.estimatedLevel
        }

        val totalErrors = profile.correctionsDelivered
        val totalUtterances = profile.totalUtterances
        val errorRate = totalErrors.toFloat() / totalUtterances.toFloat()

        val retrySuccessRate = if (profile.correctionsDelivered > 0) {
            profile.successfulRetries.toFloat() / profile.correctionsDelivered.toFloat()
        } else {
            1.0f
        }

        val masteredCount = masteryList.count { it.isMastered }
        val confidence = profile.speakingConfidenceScore

        // Multi-signal weighted score: 0 to 100
        val accuracyComponent = ((1f - errorRate.coerceIn(0f, 1f)) * 40f) // max 40
        val retryComponent = (retrySuccessRate.coerceIn(0f, 1f) * 20f) // max 20
        val confidenceComponent = ((confidence / 100f).coerceIn(0f, 1f) * 20f) // max 20
        val masteryComponent = ((masteredCount / 10f).coerceIn(0f, 1f) * 20f) // max 20

        val totalScore = accuracyComponent + retryComponent + confidenceComponent + masteryComponent

        return when {
            totalScore >= 85f && totalUtterances >= 50 -> EstimatedLevel.C1
            totalScore >= 70f && totalUtterances >= 30 -> EstimatedLevel.B2
            totalScore >= 55f && totalUtterances >= 15 -> EstimatedLevel.B1
            totalScore >= 35f -> EstimatedLevel.A2
            else -> EstimatedLevel.A1
        }
    }

    fun generateSessionSummary(
        sessionId: String,
        sessionDurationMs: Long,
        userTurnsCount: Int,
        events: List<LearningEvent>,
        speakingConfidence: Float
    ): SessionLearningSummary {
        val durationSeconds = sessionDurationMs / 1000L
        val speakingMinutes = (durationSeconds / 60).toInt().coerceAtLeast(1)

        val corrections = events.filter { it.type == LearningEventType.CORRECTION }
        val successfulRetries = events.filter { it.type == LearningEventType.SUCCESSFUL_RETRY }
        val failedRetries = events.filter { it.type == LearningEventType.FAILED_RETRY }

        val improvedConcepts = events
            .filter { it.type == LearningEventType.SUCCESSFUL_RETRY || it.type == LearningEventType.MASTERY_GAIN }
            .map { it.conceptId }
            .distinct()

        val weakConcepts = events
            .filter { it.type == LearningEventType.FAILED_RETRY || (it.type == LearningEventType.CORRECTION && it.severity >= CorrectionSeverity.IMPORTANT) }
            .map { it.conceptId }
            .distinct()

        val cleanTurnRatio = if (userTurnsCount > 0) {
            ((userTurnsCount - corrections.size).toFloat() / userTurnsCount.toFloat()).coerceIn(0f, 1f)
        } else {
            0.85f
        }

        val grammarScore = (cleanTurnRatio * 100).toInt().coerceIn(50, 100)
        val fluencyScore = ((speakingConfidence * 0.4f) + (cleanTurnRatio * 60f)).toInt().coerceIn(50, 100)
        val pronunciationScore = (85 + (speakingConfidence * 0.1f)).toInt().coerceIn(75, 98)
        val vocabularyScore = (78 + (userTurnsCount * 2)).coerceIn(65, 95)

        val trend = when {
            successfulRetries.isNotEmpty() || cleanTurnRatio >= 0.8f -> "Improving"
            failedRetries.size > 2 -> "Developing"
            else -> "Steady"
        }

        return SessionLearningSummary(
            sessionId = sessionId,
            durationSeconds = durationSeconds,
            speakingMinutes = speakingMinutes,
            userTurnsCount = userTurnsCount,
            correctionsCount = corrections.size,
            successfulRetriesCount = successfulRetries.size,
            conceptsMasteredOrImproved = improvedConcepts,
            conceptsNeedingPractice = weakConcepts,
            newExpressionsLearned = emptyList(),
            fluencyScore = fluencyScore,
            grammarScore = grammarScore,
            pronunciationScore = pronunciationScore,
            vocabularyScore = vocabularyScore,
            confidenceTrend = trend
        )
    }
}
