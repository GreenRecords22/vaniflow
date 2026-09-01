package com.vaniflow.app.engine.speech

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

class PronunciationAnalyzerTest {

    private lateinit var featureExtractor: SpeechFeatureExtractor
    private lateinit var qualityAnalyzer: SpeechQualityAnalyzer
    private lateinit var pronunciationAnalyzer: PronunciationAnalyzer

    @Before
    fun setUp() {
        featureExtractor = SpeechFeatureExtractor()
        qualityAnalyzer = SpeechQualityAnalyzer(featureExtractor)
        pronunciationAnalyzer = PronunciationAnalyzer(featureExtractor, qualityAnalyzer)
    }

    @Test
    fun `analyze returns NOT_ENOUGH_PRONUNCIATION_EVIDENCE when audio is too brief`() {
        val segment = SpeechAudioSegment(samples = ShortArray(3200), durationMs = 200L)
        val result = pronunciationAnalyzer.analyze(segment, "Hello there")

        assertEquals(QualitativePronunciationRating.NOT_ENOUGH_PRONUNCIATION_EVIDENCE, result.qualitativeRating)
        assertFalse(result.phonemeEvidenceAvailable)
        assertNull(result.practiceSoundSuggestion)
    }

    @Test
    fun `analyze returns NOT_ENOUGH_PRONUNCIATION_EVIDENCE when audio is empty or blank transcript`() {
        val segment = SpeechAudioSegment(samples = ShortArray(0), durationMs = 0L)
        val result = pronunciationAnalyzer.analyze(segment, "")

        assertEquals(QualitativePronunciationRating.NOT_ENOUGH_PRONUNCIATION_EVIDENCE, result.qualitativeRating)
        assertFalse(result.phonemeEvidenceAvailable)
    }

    @Test
    fun `analyze identifies target pronunciation sound and offers coaching tip without claiming phoneme evidence`() {
        val sampleRate = 16000
        val totalSamples = sampleRate * 2
        val samples = ShortArray(totalSamples)

        for (i in 0 until totalSamples) {
            samples[i] = (6000.0 * sin(2.0 * Math.PI * 300.0 * i / sampleRate)).toInt().toShort()
        }

        val segment = SpeechAudioSegment(samples = samples, durationMs = 2000L)
        val transcript = "I think we should walk through the park"
        val result = pronunciationAnalyzer.analyze(segment, transcript)

        assertTrue(result.audioQualityEvidenceAvailable)
        assertFalse("Phoneme evidence MUST remain false", result.phonemeEvidenceAvailable)
        assertTrue(result.observedPhonemePatterns.contains("th_unvoiced"))
        assertNotNull(result.practiceSoundSuggestion)
        assertTrue(result.practiceSoundSuggestion!!.contains("teeth"))
    }
}
