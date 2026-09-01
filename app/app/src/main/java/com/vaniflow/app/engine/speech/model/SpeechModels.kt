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
 * Truthful qualitative utterance clarity / pronunciation evidence rating.
 * Note: Until an acoustic phoneme alignment model is integrated, this represents
 * utterance acoustic clarity and never claims phoneme-level measurement.
 */
enum class QualitativePronunciationRating(val displayLabel: String) {
    NOT_ENOUGH_DATA("Not enough pronunciation evidence"),
    NOT_ENOUGH_PRONUNCIATION_EVIDENCE("Not enough pronunciation evidence"),
    DEVELOPING("Developing Clarity"),
    CLEAR("Clear Speech"),
    NATURAL("Natural Clarity")
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
 * Truthful pronunciation & speech evidence model.
 *
 * Explicitly separates:
 * 1. Audio Quality Evidence
 * 2. Temporal Fluency Evidence
 * 3. Transcript Evidence
 * 4. Pronunciation Practice Candidates / Targets
 * 5. Actual Phoneme Acoustic Evidence (remains false until an acoustic phoneme model is active)
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
    val audioQualityEvidenceAvailable: Boolean = false,
    val fluencyEvidenceAvailable: Boolean = false,
    val transcriptEvidenceAvailable: Boolean = false,
    val phonemeEvidenceAvailable: Boolean = false, // Strictly false in current architecture
    val practiceTargetId: String? = null,
    val practiceTargetLabel: String? = null,
    val observedPhonemePatterns: List<String> = emptyList(),
    val qualitativeRating: QualitativePronunciationRating = QualitativePronunciationRating.NOT_ENOUGH_PRONUNCIATION_EVIDENCE,
    val confidence: Float = 0f,
    val practiceSoundSuggestion: String? = null,
    val timestampEpochMs: Long = System.currentTimeMillis()
)
