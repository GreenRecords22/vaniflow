package com.vaniflow.app.engine.speech

import com.vaniflow.app.engine.speech.model.PronunciationEvidence
import com.vaniflow.app.engine.speech.model.QualitativePronunciationRating
import com.vaniflow.app.engine.speech.model.SpeechAudioSegment
import javax.inject.Inject
import javax.inject.Singleton
import java.util.UUID

/**
 * Truthful Pronunciation & Speech Candidate Analyzer.
 *
 * Core Principles:
 * 1. Separates Audio Quality Evidence, Fluency Evidence, Transcript Evidence, and Practice Targets.
 * 2. Does NOT mark phoneme evidence as available (`phonemeEvidenceAvailable = false`) until an actual
 *    acoustic phoneme alignment model runs.
 * 3. Treats identified target patterns (e.g. unvoiced /θ/, /v/ vs /w/, past "-ed") as
 *    *Pronunciation Practice Candidates / Focus Targets*, never as confirmed learner mispronunciations.
 */
@Singleton
class PronunciationAnalyzer @Inject constructor(
    private val featureExtractor: SpeechFeatureExtractor,
    private val qualityAnalyzer: SpeechQualityAnalyzer
) {

    data class TargetPronunciationCandidate(
        val patternId: String,
        val targetSound: String,
        val keywords: List<String>,
        val coachingTip: String
    )

    private val practiceCandidates = listOf(
        TargetPronunciationCandidate(
            patternId = "th_unvoiced",
            targetSound = "unvoiced 'th' sound (/θ/)",
            keywords = listOf("think", "thought", "thanks", "thank", "thirty", "through", "thing", "three"),
            coachingTip = "Place your tongue gently between your teeth for 'think' rather than 'sink'."
        ),
        TargetPronunciationCandidate(
            patternId = "v_w_distinction",
            targetSound = "'v' vs 'w' clarity",
            keywords = listOf("very", "voice", "video", "village", "water", "world", "work", "welcome"),
            coachingTip = "Touch top teeth to lower lip for 'v', and round your lips for 'w'."
        ),
        TargetPronunciationCandidate(
            patternId = "past_ed_ending",
            targetSound = "past tense '-ed' sound",
            keywords = listOf("walked", "talked", "asked", "watched", "worked", "started", "decided"),
            coachingTip = "Be sure to cleanly pronounce the ending sound in past tense verbs like 'walked' (/t/).' "
        ),
        TargetPronunciationCandidate(
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

        // 1. Guard against empty, sub-minimum duration (<600ms), or unusable acoustic signals
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
                audioQualityEvidenceAvailable = quality.isSignalUsable,
                fluencyEvidenceAvailable = false,
                transcriptEvidenceAvailable = trimmed.isNotBlank(),
                phonemeEvidenceAvailable = false, // Strictly false: no acoustic phoneme alignment model
                practiceTargetId = null,
                practiceTargetLabel = null,
                observedPhonemePatterns = emptyList(),
                qualitativeRating = QualitativePronunciationRating.NOT_ENOUGH_PRONUNCIATION_EVIDENCE,
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

        // 2. Identify active target pronunciation practice candidates present in this turn
        val observedCandidates = mutableListOf<String>()
        var primaryCandidateId: String? = null
        var primaryCandidateLabel: String? = null
        var practiceSuggestion: String? = null

        for (candidate in practiceCandidates) {
            val hasKeyword = words.any { word -> candidate.keywords.contains(word) }
            if (hasKeyword) {
                observedCandidates.add(candidate.patternId)
                if (practiceSuggestion == null) {
                    primaryCandidateId = candidate.patternId
                    primaryCandidateLabel = candidate.targetSound
                    practiceSuggestion = candidate.coachingTip
                }
            }
        }

        // 3. Acoustic Signal Quality Score (0..100) — Represents audio capture health, NOT phoneme accuracy
        val snrScore = (quality.snrDb / 20f * 60f).coerceIn(0f, 60f)
        val clippingPenalty = (quality.clippingRatio * 100f).coerceIn(0f, 30f)
        val rmsScore = if (quality.rmsEnergyDbfs in -40.0f..-12.0f) 40f else 20f
        val signalQualityScore = (snrScore + rmsScore - clippingPenalty).coerceIn(20f, 100f)

        // 4. Utterance Acoustic Clarity Rating
        val clarityRating = when {
            signalQualityScore >= 80f && features.voicedDurationMs >= 1000L && words.size >= 4 -> QualitativePronunciationRating.NATURAL
            signalQualityScore >= 60f && features.voicedDurationMs >= 500L -> QualitativePronunciationRating.CLEAR
            signalQualityScore >= 40f -> QualitativePronunciationRating.DEVELOPING
            else -> QualitativePronunciationRating.NOT_ENOUGH_PRONUNCIATION_EVIDENCE
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
            audioQualityEvidenceAvailable = true,
            fluencyEvidenceAvailable = true,
            transcriptEvidenceAvailable = true,
            phonemeEvidenceAvailable = false, // Strictly false: Good SNR/RMS is NOT phoneme evidence
            practiceTargetId = primaryCandidateId,
            practiceTargetLabel = primaryCandidateLabel,
            observedPhonemePatterns = observedCandidates,
            qualitativeRating = clarityRating,
            confidence = confidence,
            practiceSoundSuggestion = practiceSuggestion
        )
    }
}
