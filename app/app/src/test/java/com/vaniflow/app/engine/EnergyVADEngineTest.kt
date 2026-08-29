package com.vaniflow.app.engine

import com.vaniflow.app.engine.audio.AudioConfig
import com.vaniflow.app.engine.audio.EnergyVADEngine
import com.vaniflow.app.engine.audio.VADState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.math.sin

class EnergyVADEngineTest {

    private lateinit var vadEngine: EnergyVADEngine

    @Before
    fun setup() {
        vadEngine = EnergyVADEngine()
    }

    /**
     * Generates a synthetic PCM sine wave buffer representing speech audio.
     */
    private fun generateSineWave(amplitude: Short = 12000, frequencyHz: Double = 440.0, numSamples: Int = 512): ShortArray {
        val samples = ShortArray(numSamples)
        for (i in 0 until numSamples) {
            val angle = 2.0 * Math.PI * i * (frequencyHz / AudioConfig.SAMPLE_RATE_HZ)
            samples[i] = (sin(angle) * amplitude).toInt().toShort()
        }
        return samples
    }

    /**
     * Generates a near-silent PCM buffer representing background noise.
     */
    private fun generateSilence(amplitude: Short = 40, numSamples: Int = 512): ShortArray {
        val samples = ShortArray(numSamples)
        for (i in 0 until numSamples) {
            samples[i] = (amplitude * if (i % 2 == 0) 1 else -1).toShort()
        }
        return samples
    }

    @Test
    fun testInitialStateIsSilence() {
        assertEquals(VADState.SILENCE, vadEngine.state.value)
    }

    @Test
    fun testSilenceFramesMaintainSilenceState() {
        val silence = generateSilence()
        repeat(5) {
            val decision = vadEngine.processFrame(silence, silence.size)
            assertEquals(VADState.SILENCE, decision.state)
            assertFalse(decision.isSpeech)
            assertTrue("dBFS should be low for silence", decision.energyDbfs < -45.0f)
        }
    }

    @Test
    fun testSpeechOnsetTriggersSpeechStarted() {
        val speech = generateSineWave(amplitude = 15000)

        // Frame 1: speech detected, but needs onset confirmation
        val decision1 = vadEngine.processFrame(speech, speech.size)

        // Frame 2: consecutive speech frame meets onset threshold
        val decision2 = vadEngine.processFrame(speech, speech.size)
        assertEquals(VADState.SPEECH_STARTED, decision2.state)
        assertTrue(decision2.isSpeech)
        assertTrue(decision2.energyDbfs > -20.0f)
    }

    @Test
    fun testContinuingSpeechMaintainsSpeechState() {
        val speech = generateSineWave(amplitude = 15000)

        // Trigger onset
        vadEngine.processFrame(speech, speech.size)
        vadEngine.processFrame(speech, speech.size)

        // Frame 3: continuing speech
        val decision3 = vadEngine.processFrame(speech, speech.size)
        assertEquals(VADState.SPEECH_CONTINUING, decision3.state)
        assertTrue(decision3.isSpeech)
    }

    @Test
    fun testSilenceTimeoutTriggersSpeechEnded() {
        val speech = generateSineWave(amplitude = 15000)
        val silence = generateSilence()

        // 1. Start speech
        vadEngine.processFrame(speech, speech.size)
        vadEngine.processFrame(speech, speech.size)
        assertEquals(VADState.SPEECH_STARTED, vadEngine.state.value)

        // 2. Feed brief silence (< 900ms) - should stay in hangover (SPEECH_CONTINUING)
        // 512 samples @ 16kHz = 32ms per frame
        // 10 frames = 320ms (< 900ms)
        repeat(10) {
            val decision = vadEngine.processFrame(silence, silence.size)
            assertEquals(VADState.SPEECH_CONTINUING, decision.state)
        }

        // 3. Feed enough silence to cross 900ms (total > 30 frames * 32ms = 960ms)
        var sawSpeechEnded = false
        repeat(25) {
            val decision = vadEngine.processFrame(silence, silence.size)
            if (decision.state == VADState.SPEECH_ENDED) {
                sawSpeechEnded = true
            }
        }

        assertTrue("VAD should trigger SPEECH_ENDED when silence timeout is crossed", sawSpeechEnded)
    }

    @Test
    fun testResetClearsState() {
        val speech = generateSineWave(amplitude = 15000)
        vadEngine.processFrame(speech, speech.size)
        vadEngine.processFrame(speech, speech.size)

        vadEngine.reset()
        assertEquals(VADState.SILENCE, vadEngine.state.value)
    }
}
