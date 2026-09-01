package com.vaniflow.app.engine.speech

import com.vaniflow.app.engine.speech.model.SpeechAudioSegment
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.math.sin

class SpeechQualityAnalyzerTest {

    private lateinit var featureExtractor: SpeechFeatureExtractor
    private lateinit var qualityAnalyzer: SpeechQualityAnalyzer

    @Before
    fun setUp() {
        featureExtractor = SpeechFeatureExtractor()
        qualityAnalyzer = SpeechQualityAnalyzer(featureExtractor)
    }

    @Test
    fun `analyze marks short or empty audio as unusable`() {
        val empty = SpeechAudioSegment(samples = ShortArray(0), durationMs = 0L)
        val result = qualityAnalyzer.analyze(empty)

        assertFalse(result.isSignalUsable)
        assertTrue(result.qualitySummary.contains("Insufficient"))
    }

    @Test
    fun `analyze marks clipped audio as unusable`() {
        val clippedSamples = ShortArray(16000) { 32767.toShort() }
        val segment = SpeechAudioSegment(samples = clippedSamples, durationMs = 1000L)
        val result = qualityAnalyzer.analyze(segment)

        assertFalse(result.isSignalUsable)
        assertTrue(result.clippingRatio > 0.05f)
        assertTrue(result.qualitySummary.contains("clipping"))
    }

    @Test
    fun `analyze marks clean tone as usable signal`() {
        val sampleRate = 16000
        val totalSamples = sampleRate * 2 // 2 seconds
        val samples = ShortArray(totalSamples)

        for (i in 0 until totalSamples) {
            samples[i] = (6000.0 * sin(2.0 * Math.PI * 300.0 * i / sampleRate)).toInt().toShort()
        }

        val segment = SpeechAudioSegment(samples = samples, durationMs = 2000L)
        val result = qualityAnalyzer.analyze(segment)

        assertTrue(result.isSignalUsable)
        assertTrue(result.snrDb >= 0f)
    }
}
