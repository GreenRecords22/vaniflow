package com.vaniflow.app.engine.speech

import com.vaniflow.app.engine.speech.model.HesitationType
import com.vaniflow.app.engine.speech.model.QualitativeFluencyRating
import com.vaniflow.app.engine.speech.model.SpeechAudioSegment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.math.sin

class FluencyAnalyzerTest {

    private lateinit var featureExtractor: SpeechFeatureExtractor
    private lateinit var fluencyAnalyzer: FluencyAnalyzer

    @Before
    fun setUp() {
        featureExtractor = SpeechFeatureExtractor()
        fluencyAnalyzer = FluencyAnalyzer(featureExtractor)
    }

    @Test
    fun `analyze returns insufficient data for empty or very brief transcript`() {
        val segment = SpeechAudioSegment(samples = ShortArray(1600), durationMs = 100L)
        val result = fluencyAnalyzer.analyze(segment, "")

        assertEquals(QualitativeFluencyRating.INSUFFICIENT_DATA, result.qualitativeRating)
        assertEquals(0f, result.wordsPerMinute, 0.01f)
    }

    @Test
    fun `analyze computes natural speaking rate for typical conversation`() {
        val sampleRate = 16000
        val durationSeconds = 3.0
        val totalSamples = (sampleRate * durationSeconds).toInt()
        val samples = ShortArray(totalSamples)

        for (i in 0 until totalSamples) {
            samples[i] = (7000.0 * sin(2.0 * Math.PI * 300.0 * i / sampleRate)).toInt().toShort()
        }

        val segment = SpeechAudioSegment(samples = samples, durationMs = 3000L)
        val transcript = "I really enjoy practicing English every single morning"
        val result = fluencyAnalyzer.analyze(segment, transcript)

        assertTrue("WPM should be between 100 and 200", result.wordsPerMinute in 100f..200f)
        assertTrue(result.syllablesPerSecond > 1.0f)
        assertNotEquals(QualitativeFluencyRating.INSUFFICIENT_DATA, result.qualitativeRating)
    }

    @Test
    fun `analyze does not penalize natural thinking pauses as hesitation`() {
        // Create audio with 400ms pause in between two 1-second speech bursts
        val sampleRate = 16000
        val totalSamples = (sampleRate * 2.4).toInt()
        val samples = ShortArray(totalSamples)

        // Burst 1: 0 to 1s
        for (i in 0 until sampleRate) {
            samples[i] = (7000.0 * sin(2.0 * Math.PI * 300.0 * i / sampleRate)).toInt().toShort()
        }
        // Silence: 1s to 1.4s (400ms normal thinking pause)
        // Burst 2: 1.4s to 2.4s
        for (i in (sampleRate * 1.4).toInt() until totalSamples) {
            samples[i] = (7000.0 * sin(2.0 * Math.PI * 300.0 * i / sampleRate)).toInt().toShort()
        }

        val segment = SpeechAudioSegment(samples = samples, durationMs = 2400L)
        val transcript = "I think that coffee is delicious"
        val result = fluencyAnalyzer.analyze(segment, transcript)

        // Normal thinking pause (400ms) should be NORMAL_PAUSE or STEADY, never REPEATED_HESITATION
        assertTrue(
            "Normal thinking pause should not trigger hesitation penalty",
            result.hesitationType == HesitationType.NORMAL_PAUSE || result.hesitationType == HesitationType.STEADY
        )
    }
}
