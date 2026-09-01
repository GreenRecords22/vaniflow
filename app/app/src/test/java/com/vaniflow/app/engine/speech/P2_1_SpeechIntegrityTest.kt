package com.vaniflow.app.engine.speech

import com.vaniflow.app.engine.learning.tutor.ProgressCalculationEngine
import com.vaniflow.app.engine.learning.tutor.model.LearningEvent
import com.vaniflow.app.engine.speech.model.HesitationType
import com.vaniflow.app.engine.speech.model.QualitativeFluencyRating
import com.vaniflow.app.engine.speech.model.QualitativePronunciationRating
import com.vaniflow.app.engine.speech.model.SpeechAudioSegment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.math.sin

/**
 * Production Speech Intelligence Integrity Tests for P2.1 Hardening.
 *
 * Verifies that:
 * 1. Audio quality, temporal fluency, and transcript keywords are never conflated with acoustic phoneme evidence.
 * 2. `phonemeEvidenceAvailable` strictly remains `false` across all scenarios until an acoustic phoneme aligner runs.
 * 3. Target sound patterns are treated strictly as practice candidates, never as confirmed learner mispronunciations.
 * 4. Normal 200-800ms pauses are not penalized.
 * 5. No arbitrary pronunciation percentages are manufactured.
 */
class P2_1_SpeechIntegrityTest {

    private lateinit var featureExtractor: SpeechFeatureExtractor
    private lateinit var qualityAnalyzer: SpeechQualityAnalyzer
    private lateinit var fluencyAnalyzer: FluencyAnalyzer
    private lateinit var pronunciationAnalyzer: PronunciationAnalyzer
    private lateinit var progressEngine: ProgressCalculationEngine

    @Before
    fun setUp() {
        featureExtractor = SpeechFeatureExtractor()
        qualityAnalyzer = SpeechQualityAnalyzer(featureExtractor)
        fluencyAnalyzer = FluencyAnalyzer(featureExtractor)
        pronunciationAnalyzer = PronunciationAnalyzer(featureExtractor, qualityAnalyzer)
        progressEngine = ProgressCalculationEngine()
    }

    private fun createCleanToneSegment(durationMs: Long, sampleRate: Int = 16000): SpeechAudioSegment {
        val totalSamples = ((durationMs * sampleRate) / 1000L).toInt()
        val samples = ShortArray(totalSamples)
        for (i in 0 until totalSamples) {
            samples[i] = (6000.0 * sin(2.0 * Math.PI * 300.0 * i / sampleRate)).toInt().toShort()
        }
        return SpeechAudioSegment(samples = samples, sampleRateHz = sampleRate, durationMs = durationMs)
    }

    @Test
    fun `requirement 1 - good SNR and clear audio does NOT create phoneme evidence`() {
        val audio = createCleanToneSegment(2500L)
        val quality = qualityAnalyzer.analyze(audio)
        val evidence = pronunciationAnalyzer.analyze(audio, "This is clean spoken English")

        assertTrue("Acoustic signal is usable", quality.isSignalUsable)
        assertTrue("Audio quality evidence is available", evidence.audioQualityEvidenceAvailable)
        assertFalse("Phoneme evidence MUST remain false", evidence.phonemeEvidenceAvailable)
    }

    @Test
    fun `requirement 2 - transcript with think identifies practice candidate but phoneme evidence remains false`() {
        val audio = createCleanToneSegment(2000L)
        val transcript = "I think this is very helpful"
        val evidence = pronunciationAnalyzer.analyze(audio, transcript)

        assertEquals("th_unvoiced", evidence.practiceTargetId)
        assertNotNull(evidence.practiceSoundSuggestion)
        assertTrue(evidence.practiceSoundSuggestion!!.contains("teeth"))
        assertFalse("Phoneme evidence MUST remain false despite keyword presence", evidence.phonemeEvidenceAvailable)
    }

    @Test
    fun `requirement 3 - transcript with very identifies practice candidate with no error claim`() {
        val audio = createCleanToneSegment(2000L)
        val transcript = "We are very happy to welcome you to our world"
        val evidence = pronunciationAnalyzer.analyze(audio, transcript)

        assertEquals("v_w_distinction", evidence.practiceTargetId)
        assertNotNull(evidence.practiceSoundSuggestion)
        assertTrue(evidence.practiceSoundSuggestion!!.contains("teeth"))
        assertFalse("Phoneme evidence MUST remain false", evidence.phonemeEvidenceAvailable)
    }

    @Test
    fun `requirement 4 - good RMS energy does NOT create phoneme evidence`() {
        val audio = createCleanToneSegment(2000L)
        val features = featureExtractor.extractAcousticFeatures(audio)
        val evidence = pronunciationAnalyzer.analyze(audio, "Hello there world")

        assertTrue("Mean RMS is in good range", features.meanRmsDbfs > -30f)
        assertFalse("Good RMS is NOT phoneme evidence", evidence.phonemeEvidenceAvailable)
    }

    @Test
    fun `requirement 5 - high confidence transcript does NOT create phoneme evidence`() {
        val audio = createCleanToneSegment(3000L)
        val evidence = pronunciationAnalyzer.analyze(audio, "This is an extraordinarily clean fluent sentence with high confidence")

        assertTrue("Confidence is high for long clean turn", evidence.confidence >= 0.8f)
        assertFalse("High STT confidence is NOT phoneme evidence", evidence.phonemeEvidenceAvailable)
    }

    @Test
    fun `requirement 6 - normal 200 to 800ms thinking pause does not reduce fluency rating`() {
        val sampleRate = 16000
        val totalSamples = (sampleRate * 2.4).toInt()
        val samples = ShortArray(totalSamples)

        // Voiced burst 1: 0 to 1.0s
        for (i in 0 until sampleRate) {
            samples[i] = (7000.0 * sin(2.0 * Math.PI * 300.0 * i / sampleRate)).toInt().toShort()
        }
        // Normal thinking pause: 1.0s to 1.4s (400ms)
        // Voiced burst 2: 1.4s to 2.4s
        for (i in (sampleRate * 1.4).toInt() until totalSamples) {
            samples[i] = (7000.0 * sin(2.0 * Math.PI * 300.0 * i / sampleRate)).toInt().toShort()
        }

        val segment = SpeechAudioSegment(samples = samples, durationMs = 2400L)
        val result = fluencyAnalyzer.analyze(segment, "I think that this design is clear")

        assertTrue(
            "Normal thinking pause should be NORMAL_PAUSE or STEADY",
            result.hesitationType == HesitationType.NORMAL_PAUSE || result.hesitationType == HesitationType.STEADY
        )
        assertTrue(
            "Fluency rating should remain Natural or Clear",
            result.qualitativeRating == QualitativeFluencyRating.NATURAL || result.qualitativeRating == QualitativeFluencyRating.CLEAR
        )
    }

    @Test
    fun `requirement 7 - empty audio produces no pronunciation evidence`() {
        val emptyAudio = SpeechAudioSegment(samples = ShortArray(0), durationMs = 0L)
        val evidence = pronunciationAnalyzer.analyze(emptyAudio, "")

        assertFalse("Phoneme evidence is false", evidence.phonemeEvidenceAvailable)
        assertFalse("Audio quality evidence is false", evidence.audioQualityEvidenceAvailable)
        assertEquals(QualitativePronunciationRating.NOT_ENOUGH_PRONUNCIATION_EVIDENCE, evidence.qualitativeRating)
        assertNull(evidence.practiceSoundSuggestion)
    }

    @Test
    fun `requirement 8 - very short audio produces insufficient evidence`() {
        val shortAudio = SpeechAudioSegment(samples = ShortArray(3200), durationMs = 200L)
        val evidence = pronunciationAnalyzer.analyze(shortAudio, "Hi")

        assertFalse("Phoneme evidence is false", evidence.phonemeEvidenceAvailable)
        assertEquals(QualitativePronunciationRating.NOT_ENOUGH_PRONUNCIATION_EVIDENCE, evidence.qualitativeRating)
    }

    @Test
    fun `requirement 9 - no arbitrary pronunciation percentage is generated in session summary`() {
        val audio = createCleanToneSegment(2000L)
        val evidence = pronunciationAnalyzer.analyze(audio, "I think we should walk through the park")

        val summary = progressEngine.generateSessionSummary(
            sessionId = "sess_1",
            sessionDurationMs = 60000L,
            userTurnsCount = 4,
            events = emptyList(),
            speakingConfidence = 80f,
            speechEvidences = listOf(evidence)
        )

        // Pronunciation score must be 0 (unmeasured), not fake 85% or 90%
        assertEquals(0, summary.pronunciationScore)
        assertTrue(summary.pronunciationEvidenceState.contains("Practice Target") || summary.pronunciationEvidenceState.contains("Audio Clarity"))
    }

    @Test
    fun `requirement 10 - progress calculation does not treat practice targets as errors`() {
        val events = listOf(
            LearningEvent(
                type = com.vaniflow.app.engine.learning.tutor.model.LearningEventType.PRONUNCIATION_OBSERVED,
                conceptId = "th_unvoiced",
                category = com.vaniflow.app.engine.learning.tutor.EnglishErrorCategory.FLUENCY_FILLER,
                severity = com.vaniflow.app.engine.learning.tutor.CorrectionSeverity.STYLE,
                originalUtterance = "I think about this every day",
                isSuccess = true, // Not an error
                sessionId = "sess_1",
                confidenceImpact = 0f
            )
        )

        val audio = createCleanToneSegment(2000L)
        val evidence = pronunciationAnalyzer.analyze(audio, "I think about this every day")

        val summary = progressEngine.generateSessionSummary(
            sessionId = "sess_1",
            sessionDurationMs = 60000L,
            userTurnsCount = 4,
            events = events,
            speakingConfidence = 80f,
            speechEvidences = listOf(evidence)
        )

        assertEquals("Corrections count must be 0", 0, summary.correctionsCount)
        assertEquals("Clarity rating should be Natural for 0 errors", "Natural", summary.clarityRating)
    }
}
