package com.vaniflow.app.engine.learning.tutor

import com.vaniflow.app.engine.learning.tutor.model.LearningEvent
import com.vaniflow.app.engine.learning.tutor.model.LearningEventType
import com.vaniflow.app.engine.learning.tutor.model.MasteryState
import com.vaniflow.app.engine.learning.tutor.model.SessionLearningSummary
import com.vaniflow.app.engine.speech.model.PronunciationEvidence
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProgressCalculationEngine @Inject constructor() {

    fun estimateCefrLevel(
        profile: LearnerProfile,
        masteryList: List<MasteryState>
    ): EstimatedLevel {
        // Require at least 15 utterances across sessions before establishing higher CEFR levels
        if (profile.totalUtterances < 15) {
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
        speakingConfidence: Float,
        speechEvidences: List<PronunciationEvidence> = emptyList()
    ): SessionLearningSummary {
        val durationSeconds = sessionDurationMs / 1000L
        val speakingMinutes = (durationSeconds / 60).toInt().coerceAtLeast(1)

        val corrections = events.filter { it.type == LearningEventType.CORRECTION }
        val successfulRetries = events.filter { it.type == LearningEventType.SUCCESSFUL_RETRY }
        val failedRetries = events.filter { it.type == LearningEventType.FAILED_RETRY }
        val vocabLearned = events.filter { it.type == LearningEventType.VOCABULARY_LEARNED }.map { it.conceptId }.distinct()

        val improvedConcepts = events
            .filter { it.type == LearningEventType.SUCCESSFUL_RETRY || it.type == LearningEventType.MASTERY_GAIN || it.type == LearningEventType.PRONUNCIATION_IMPROVEMENT }
            .map { it.conceptId }
            .distinct()

        val weakConcepts = events
            .filter { it.type == LearningEventType.FAILED_RETRY || (it.type == LearningEventType.CORRECTION && it.severity >= CorrectionSeverity.IMPORTANT) }
            .map { it.conceptId }
            .distinct()

        val cleanTurnRatio = if (userTurnsCount > 0) {
            ((userTurnsCount - corrections.size).toFloat() / userTurnsCount.toFloat()).coerceIn(0f, 1f)
        } else {
            1.0f
        }

        // Evidence-based grammar & fluency scores
        val grammarScore = (cleanTurnRatio * 100).toInt().coerceIn(50, 100)
        val fluencyScore = ((speakingConfidence * 0.4f) + (cleanTurnRatio * 60f)).toInt().coerceIn(50, 100)

        // Real Speech Insights
        val validSpeechEvidences = speechEvidences.filter { it.phonemeEvidenceAvailable }
        val totalPauses = speechEvidences.sumOf { it.pauseCount }
        val avgWpm = if (speechEvidences.isNotEmpty()) {
            val nonZeroWpm = speechEvidences.filter { it.speakingRateWpm > 0 }
            if (nonZeroWpm.isNotEmpty()) nonZeroWpm.map { it.speakingRateWpm }.average().toFloat() else 0f
        } else {
            0f
        }

        val practiceAreas = speechEvidences
            .flatMap { it.observedPhonemePatterns }
            .distinct()
            .map { it.replace('_', ' ').replaceFirstChar { c -> c.uppercaseChar() } }

        val pronunciationState = when {
            speechEvidences.isEmpty() || speechEvidences.none { it.audioQualityEvidenceAvailable } -> "Not enough pronunciation evidence"
            speechEvidences.any { it.observedPhonemePatterns.isNotEmpty() } -> {
                val target = speechEvidences.firstNotNullOfOrNull { it.practiceTargetLabel }
                    ?: speechEvidences.firstNotNullOfOrNull { it.observedPhonemePatterns.firstOrNull() }?.replace('_', ' ')?.replaceFirstChar { c -> c.uppercaseChar() }
                if (target != null) "Practice Target: $target" else "Audio Clarity: Clear"
            }
            speechEvidences.all { it.qualitativeRating.name == "NATURAL" } -> "Audio Clarity: Natural"
            speechEvidences.any { it.qualitativeRating.name == "CLEAR" || it.qualitativeRating.name == "NATURAL" } -> "Audio Clarity: Clear"
            else -> "Audio Clarity: Developing"
        }

        val clarityRating = when {
            cleanTurnRatio >= 0.85f && !pronunciationState.contains("Developing") -> "Natural"
            cleanTurnRatio >= 0.65f -> "Clear"
            else -> "Developing"
        }

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
            newExpressionsLearned = vocabLearned,
            fluencyScore = fluencyScore,
            grammarScore = grammarScore,
            pronunciationScore = 0, // Truthful: 0 denotes unmeasured acoustic percentage
            vocabularyScore = vocabLearned.size,
            clarityRating = clarityRating,
            confidenceTrend = trend,
            pronunciationEvidenceState = pronunciationState,
            pronunciationPracticeAreas = practiceAreas,
            averageWordsPerMinute = avgWpm,
            pauseCount = totalPauses
        )
    }
}
