package com.vaniflow.app.engine.speech

import com.vaniflow.app.engine.speech.model.SpeechAudioSegment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.math.sin

class SpeechFeatureExtractorTest {

    private lateinit var extractor: SpeechFeatureExtractor

    @Before
    fun setUp() {
        extractor = SpeechFeatureExtractor()
    }

    @Test
    fun `extractAcousticFeatures returns baseline for empty audio`() {
        val emptySegment = SpeechAudioSegment(samples = ShortArray(0), durationMs = 0L)
        val features = extractor.extractAcousticFeatures(emptySegment)

        assertEquals(0, features.totalSamples)
        assertEquals(0L, features.durationMs)
        assertEquals(0f, features.snrDb, 0.01f)
        assertEquals(0, features.clippingCount)
    }

    @Test
    fun `extractAcousticFeatures computes valid SNR and energy for synthetic sine wave`() {
        val sampleRate = 16000
        val durationSeconds = 1.0
        val totalSamples = (sampleRate * durationSeconds).toInt()
        val samples = ShortArray(totalSamples)

        // 440 Hz tone at -12 dBFS (amplitude ~8000)
        for (i in 0 until totalSamples) {
            samples[i] = (8000.0 * sin(2.0 * Math.PI * 440.0 * i / sampleRate)).toInt().toShort()
        }

        val segment = SpeechAudioSegment(samples = samples, durationMs = 1000L)
        val features = extractor.extractAcousticFeatures(segment)

        assertEquals(1000L, features.durationMs)
        assertTrue("Mean RMS should be in reasonable speech range", features.meanRmsDbfs > -30.0f)
        assertTrue("Peak dBFS should be around -12 dBFS", features.peakDbfs > -15.0f && features.peakDbfs < -9.0f)
        assertTrue("SNR should be positive", features.snrDb >= 0f)
        assertEquals(0, features.clippingCount)
    }

    @Test
    fun `extractAcousticFeatures detects clipped audio samples`() {
        val samples = ShortArray(16000) { 32767.toShort() }
        val segment = SpeechAudioSegment(samples = samples, durationMs = 1000L)
        val features = extractor.extractAcousticFeatures(segment)

        assertEquals(16000, features.clippingCount)
        assertEquals(1.0f, features.clippingRatio, 0.01f)
    }

    @Test
    fun `estimateSyllableCount accurately estimates English syllables`() {
        assertEquals(0, extractor.estimateSyllableCount(""))
        assertEquals(1, extractor.estimateSyllableCount("cat"))
        assertEquals(2, extractor.estimateSyllableCount("water"))
        assertEquals(3, extractor.estimateSyllableCount("beautiful"))
        assertEquals(5, extractor.estimateSyllableCount("conversational"))
        assertEquals(5, extractor.estimateSyllableCount("pronunciation"))
    }
}
