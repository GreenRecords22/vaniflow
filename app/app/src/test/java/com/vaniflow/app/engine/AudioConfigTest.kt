package com.vaniflow.app.engine

import com.vaniflow.app.engine.audio.AudioConfig
import com.vaniflow.app.engine.audio.AudioFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class AudioConfigTest {

    @Test
    fun testAudioConfigConstants() {
        assertEquals(16000, AudioConfig.SAMPLE_RATE_HZ)
        assertEquals(512, AudioConfig.FRAME_SIZE_SAMPLES)
        assertEquals(2, AudioConfig.BYTES_PER_SAMPLE)
        assertEquals(650L, AudioConfig.SILENCE_TIMEOUT_MS)
        assertEquals(2, AudioConfig.SPEECH_ONSET_FRAMES)
    }

    @Test
    fun testAudioFrameEqualityAndHash() {
        val samples1 = shortArrayOf(100, 200, 300)
        val samples2 = shortArrayOf(100, 200, 300)
        val samples3 = shortArrayOf(400, 500, 600)

        val frame1 = AudioFrame(samples1, 3, 1000L)
        val frame2 = AudioFrame(samples2, 3, 1000L)
        val frame3 = AudioFrame(samples3, 3, 1000L)

        assertEquals(frame1, frame2)
        assertEquals(frame1.hashCode(), frame2.hashCode())
        assertNotEquals(frame1, frame3)
    }
}
