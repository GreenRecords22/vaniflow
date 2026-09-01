package com.vaniflow.app.engine.speech.model

import java.util.UUID

/**
 * Represents a raw or buffered audio segment captured during a speech turn.
 * Audio is standardized at 16 kHz Mono 16-bit PCM.
 */
data class SpeechAudioSegment(
    val samples: ShortArray,
    val sampleRateHz: Int = 16000,
    val durationMs: Long = if (sampleRateHz > 0) (samples.size.toLong() * 1000L) / sampleRateHz else 0L,
    val startTimeMs: Long = System.currentTimeMillis() - durationMs,
    val endTimeMs: Long = System.currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as SpeechAudioSegment
        if (!samples.contentEquals(other.samples)) return false
        if (sampleRateHz != other.sampleRateHz) return false
        if (durationMs != other.durationMs) return false
        return startTimeMs == other.startTimeMs
    }

    override fun hashCode(): Int {
        var result = samples.contentHashCode()
        result = 31 * result + sampleRateHz
        result = 31 * result + durationMs.hashCode()
        result = 31 * result + startTimeMs.hashCode()
        return result
    }
}

/**
 * Type of hesitation observed during speaking.
 */
enum class HesitationType(val displayLabel: String) {
    STEADY("Steady Pacing"),
    NORMAL_PAUSE("Natural Thinking Pause"),
    HESITATION("Brief Hesitation"),
    LONG_HESITATION("Extended Hesitation"),
    REPEATED_HESITATION("Frequent Hesitation")
}

/**
 * Truthful qualitative fluency rating based on evidence.
 */
enum class QualitativeFluencyRating(val displayLabel: String) {
    NATURAL("Natural Flow"),
    CLEAR("Clear & Steady"),
    DEVELOPING("Developing Fluency"),
    HESITANT("Hesitant Pacing"),
    INSUFFICIENT_DATA("Building Baseline")
}

/**
 * Truthful qualitative pronunciation rating.
 * Never outputs a fabricated score when acoustic evidence is absent.
 */
enum class QualitativePronunciationRating(val displayLabel: String) {
    NOT_ENOUGH_DATA("Not enough evidence yet"),
    DEVELOPING("Developing Clarity"),
    CLEAR("Clear Pronunciation"),
    NATURAL("Natural Pronunciation")
}

/**
 * Quality evaluation of the acoustic signal.
 */
data class SpeechQualityResult(
    val snrDb: Float,
    val noiseFloorDbfs: Float,
    val clippingCount: Int,
    val clippingRatio: Float,
    val rmsEnergyDbfs: Float,
    val isSignalUsable: Boolean,
    val qualitySummary: String
)

/**
 * Result of fluency, pause, and temporal pacing analysis.
 */
data class FluencyAnalysisResult(
    val totalDurationMs: Long,
    val voicedDurationMs: Long,
    val silenceDurationMs: Long,
    val speechToSilenceRatio: Float,
    val pauseCount: Int,
    val totalPauseDurationMs: Long,
    val averagePauseDurationMs: Long,
    val wordsPerMinute: Float,
    val syllablesPerSecond: Float,
    val hesitationType: HesitationType,
    val qualitativeRating: QualitativeFluencyRating,
    val confidence: Float
)

/**
 * Truthful pronunciation evidence model.
 */
data class PronunciationEvidence(
    val utteranceId: String = UUID.randomUUID().toString(),
    val sessionId: String? = null,
    val transcript: String,
    val audioDurationMs: Long,
    val voicedDurationMs: Long,
    val pauseCount: Int,
    val totalPauseDurationMs: Long,
    val speakingRateWpm: Float,
    val speechToSilenceRatio: Float,
    val signalQualityScore: Float, // 0..100
    val phonemeEvidenceAvailable: Boolean,
    val observedPhonemePatterns: List<String> = emptyList(),
    val qualitativeRating: QualitativePronunciationRating,
    val confidence: Float,
    val practiceSoundSuggestion: String? = null,
    val timestampEpochMs: Long = System.currentTimeMillis()
)
