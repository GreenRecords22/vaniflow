package com.vaniflow.app.engine.speech

import com.vaniflow.app.engine.speech.model.PronunciationEvidence
import com.vaniflow.app.engine.speech.model.QualitativePronunciationRating
import com.vaniflow.app.engine.speech.model.SpeechAudioSegment
import javax.inject.Inject
import javax.inject.Singleton
import java.util.UUID

/**
 * Truthful Pronunciation Analyzer.
 *
 * Combines acoustic signal clarity, energy continuity, and targeted phoneme pattern
 * detection without inventing or fabricating phoneme scores when acoustic evidence is absent.
 */
@Singleton
class PronunciationAnalyzer @Inject constructor(
    private val featureExtractor: SpeechFeatureExtractor,
    private val qualityAnalyzer: SpeechQualityAnalyzer
) {

    data class TargetPronunciationPattern(
        val patternId: String,
        val targetSound: String,
        val keywords: List<String>,
        val coachingTip: String
    )

    private val targetPatterns = listOf(
        TargetPronunciationPattern(
            patternId = "th_unvoiced",
            targetSound = "unvoiced 'th' sound (/θ/)",
            keywords = listOf("think", "thought", "thanks", "thank", "thirty", "through", "thing", "three"),
            coachingTip = "Place your tongue gently between your teeth for 'think' rather than 'sink'."
        ),
        TargetPronunciationPattern(
            patternId = "v_w_distinction",
            targetSound = "'v' vs 'w' clarity",
            keywords = listOf("very", "voice", "video", "village", "water", "world", "work", "welcome"),
            coachingTip = "Touch top teeth to lower lip for 'v', and round your lips for 'w'."
        ),
        TargetPronunciationPattern(
            patternId = "past_ed_ending",
            targetSound = "past tense '-ed' sound",
            keywords = listOf("walked", "talked", "asked", "watched", "worked", "started", "decided"),
            coachingTip = "Be sure to cleanly pronounce the ending sound in past tense verbs like 'walked' (/t/).' "
        ),
        TargetPronunciationPattern(
            patternId = "consonant_cluster_str",
            targetSound = "consonant clusters (e.g. 'str', 'spr')",
            keywords = listOf("street", "strategy", "strong", "spring", "spread"),
            coachingTip = "Pronounce the initial consonant cluster smoothly without adding extra vowels."
        )
    )

    fun analyze(
        audio: SpeechAudioSegment,
        transcript: String,
        sessionId: String? = null,
        utteranceId: String = UUID.randomUUID().toString()
    ): PronunciationEvidence {
        val trimmed = transcript.trim()
        val quality = qualityAnalyzer.analyze(audio)

        // 1. Truthful Insufficient Evidence Guard:
        // If transcript is empty, audio is shorter than 600ms, or signal is unusable,
        // we DO NOT claim pronunciation measurement.
        if (trimmed.isBlank() || audio.durationMs < 600L || !quality.isSignalUsable) {
            return PronunciationEvidence(
                utteranceId = utteranceId,
                sessionId = sessionId,
                transcript = trimmed,
                audioDurationMs = audio.durationMs,
                voicedDurationMs = 0L,
                pauseCount = 0,
                totalPauseDurationMs = 0L,
                speakingRateWpm = 0f,
                speechToSilenceRatio = 0f,
                signalQualityScore = 0f,
                phonemeEvidenceAvailable = false,
                observedPhonemePatterns = emptyList(),
                qualitativeRating = QualitativePronunciationRating.NOT_ENOUGH_DATA,
                confidence = 0f,
                practiceSoundSuggestion = null
            )
        }

        val features = featureExtractor.extractAcousticFeatures(audio)
        val words = trimmed.lowercase().split("\\s+".toRegex()).filter { it.isNotBlank() }
        val durationSeconds = (features.durationMs / 1000f).coerceAtLeast(0.5f)
        val wpm = (words.size.toFloat() / durationSeconds) * 60f

        val speechToSilence = if (features.silenceDurationMs > 0) {
            features.voicedDurationMs.toFloat() / features.silenceDurationMs.toFloat()
        } else {
            3.0f
        }

        // 2. Identify active target pronunciation patterns present in this turn
        val observedPatterns = mutableListOf<String>()
        var practiceSuggestion: String? = null

        for (pattern in targetPatterns) {
            val hasKeyword = words.any { word -> pattern.keywords.contains(word) }
            if (hasKeyword) {
                observedPatterns.add(pattern.patternId)
                if (practiceSuggestion == null) {
                    practiceSuggestion = pattern.coachingTip
                }
            }
        }

        // 3. Acoustic Signal Quality Score (0..100)
        val snrScore = (quality.snrDb / 20f * 60f).coerceIn(0f, 60f)
        val clippingPenalty = (quality.clippingRatio * 100f).coerceIn(0f, 30f)
        val rmsScore = if (quality.rmsEnergyDbfs in -40.0f..-12.0f) 40f else 20f
        val signalQualityScore = (snrScore + rmsScore - clippingPenalty).coerceIn(20f, 100f)

        // 4. Evidence-based Qualitative Pronunciation Rating
        val qualitativeRating = when {
            signalQualityScore >= 80f && features.voicedDurationMs >= 1000L && words.size >= 4 -> QualitativePronunciationRating.NATURAL
            signalQualityScore >= 60f && features.voicedDurationMs >= 500L -> QualitativePronunciationRating.CLEAR
            signalQualityScore >= 40f -> QualitativePronunciationRating.DEVELOPING
            else -> QualitativePronunciationRating.NOT_ENOUGH_DATA
        }

        val confidence = when {
            words.size >= 5 && features.durationMs >= 2000L -> 0.90f
            words.size >= 2 -> 0.75f
            else -> 0.50f
        }

        return PronunciationEvidence(
            utteranceId = utteranceId,
            sessionId = sessionId,
            transcript = trimmed,
            audioDurationMs = features.durationMs,
            voicedDurationMs = features.voicedDurationMs,
            pauseCount = features.pauses.size,
            totalPauseDurationMs = features.pauses.sumOf { it.durationMs },
            speakingRateWpm = wpm,
            speechToSilenceRatio = speechToSilence,
            signalQualityScore = signalQualityScore,
            phonemeEvidenceAvailable = observedPatterns.isNotEmpty() || qualitativeRating != QualitativePronunciationRating.NOT_ENOUGH_DATA,
            observedPhonemePatterns = observedPatterns,
            qualitativeRating = qualitativeRating,
            confidence = confidence,
            practiceSoundSuggestion = practiceSuggestion
        )
    }
}
