package com.vaniflow.app.engine.speech

import com.vaniflow.app.engine.speech.model.FluencyAnalysisResult
import com.vaniflow.app.engine.speech.model.HesitationType
import com.vaniflow.app.engine.speech.model.QualitativeFluencyRating
import com.vaniflow.app.engine.speech.model.SpeechAudioSegment
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

/**
 * Analyzes temporal continuity, pause distribution, words per minute (WPM),
 * articulation rate, and hesitation patterns to provide evidence-based fluency insights.
 *
 * Core Principle: Normal thinking pauses (200-800ms) are a natural part of conversation
 * and are never penalized as poor fluency.
 */
@Singleton
class FluencyAnalyzer @Inject constructor(
    private val featureExtractor: SpeechFeatureExtractor
) {

    fun analyze(audio: SpeechAudioSegment, transcript: String): FluencyAnalysisResult {
        val trimmed = transcript.trim()
        val words = if (trimmed.isNotBlank()) trimmed.split("\\s+".toRegex()).filter { it.isNotBlank() } else emptyList()
        val wordCount = words.size
        val syllableCount = featureExtractor.estimateSyllableCount(trimmed)

        if (wordCount == 0 || audio.durationMs < 400L) {
            return FluencyAnalysisResult(
                totalDurationMs = audio.durationMs,
                voicedDurationMs = 0L,
                silenceDurationMs = audio.durationMs,
                speechToSilenceRatio = 0f,
                pauseCount = 0,
                totalPauseDurationMs = 0L,
                averagePauseDurationMs = 0L,
                wordsPerMinute = 0f,
                syllablesPerSecond = 0f,
                hesitationType = HesitationType.STEADY,
                qualitativeRating = QualitativeFluencyRating.INSUFFICIENT_DATA,
                confidence = 0f
            )
        }

        val features = featureExtractor.extractAcousticFeatures(audio)
        val totalDurationSeconds = max(0.5f, features.durationMs / 1000f)
        val voicedDurationSeconds = max(0.2f, features.voicedDurationMs / 1000f)

        // 1. Speaking Rate & Articulation Rate
        val wpm = ((wordCount.toFloat() / totalDurationSeconds) * 60f).coerceIn(10f, 250f)
        val syllablesPerSecond = (syllableCount.toFloat() / voicedDurationSeconds).coerceIn(0.5f, 10f)

        val speechToSilenceRatio = if (features.silenceDurationMs > 0) {
            features.voicedDurationMs.toFloat() / features.silenceDurationMs.toFloat()
        } else {
            3.0f
        }

        // 2. Pause & Hesitation Analysis
        val pauses = features.pauses
        val pauseCount = pauses.size
        val totalPauseDurationMs = pauses.sumOf { it.durationMs }
        val avgPauseDurationMs = if (pauseCount > 0) totalPauseDurationMs / pauseCount else 0L

        val normalPauses = pauses.count { it.durationMs in 200L..800L }
        val hesitations = pauses.count { it.durationMs in 801L..1500L }
        val longHesitations = pauses.count { it.durationMs > 1500L }

        val hesitationType = when {
            longHesitations >= 2 || (hesitations + longHesitations) >= 3 -> HesitationType.REPEATED_HESITATION
            longHesitations == 1 -> HesitationType.LONG_HESITATION
            hesitations >= 1 -> HesitationType.HESITATION
            normalPauses >= 1 -> HesitationType.NORMAL_PAUSE
            else -> HesitationType.STEADY
        }

        // 3. Evidence-Based Qualitative Fluency Rating
        // Normal conversational English ranges from 100 to 160 WPM.
        // Pauses between 200-800ms are healthy thinking pauses.
        val qualitativeRating = when {
            wpm >= 105f && longHesitations == 0 && hesitations <= 1 && speechToSilenceRatio >= 0.7f -> QualitativeFluencyRating.NATURAL
            wpm >= 75f && longHesitations <= 1 && hesitations <= 2 -> QualitativeFluencyRating.CLEAR
            wpm >= 45f -> QualitativeFluencyRating.DEVELOPING
            else -> QualitativeFluencyRating.HESITANT
        }

        val confidence = when {
            wordCount >= 5 && features.durationMs >= 2000L -> 0.95f
            wordCount >= 2 -> 0.80f
            else -> 0.50f
        }

        return FluencyAnalysisResult(
            totalDurationMs = features.durationMs,
            voicedDurationMs = features.voicedDurationMs,
            silenceDurationMs = features.silenceDurationMs,
            speechToSilenceRatio = speechToSilenceRatio,
            pauseCount = pauseCount,
            totalPauseDurationMs = totalPauseDurationMs,
            averagePauseDurationMs = avgPauseDurationMs,
            wordsPerMinute = wpm,
            syllablesPerSecond = syllablesPerSecond,
            hesitationType = hesitationType,
            qualitativeRating = qualitativeRating,
            confidence = confidence
        )
    }
}
