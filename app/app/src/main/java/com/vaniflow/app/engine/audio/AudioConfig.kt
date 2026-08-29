package com.vaniflow.app.engine.audio

import android.media.AudioFormat
import android.media.MediaRecorder

/**
 * Standardized audio configuration for speech processing.
 * 16 kHz, Mono, 16-bit PCM (industry standard for speech AI and STT).
 */
object AudioConfig {
    const val SAMPLE_RATE_HZ = 16000
    const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
    const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    const val AUDIO_SOURCE = MediaRecorder.AudioSource.VOICE_RECOGNITION

    // 512 samples @ 16kHz = 32ms frame duration (ideal for VAD analysis)
    const val FRAME_SIZE_SAMPLES = 512
    const val BYTES_PER_SAMPLE = 2 // 16-bit PCM

    const val SILENCE_TIMEOUT_MS = 900L // 900ms silence before concluding speech turn
    const val SPEECH_ONSET_FRAMES = 2   // Consecutive speech frames to confirm speech start
}

/**
 * Represents a single audio frame captured from the microphone.
 */
data class AudioFrame(
    val samples: ShortArray,
    val length: Int,
    val timestampMs: Long = System.currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as AudioFrame
        if (!samples.contentEquals(other.samples)) return false
        if (length != other.length) return false
        return timestampMs == other.timestampMs
    }

    override fun hashCode(): Int {
        var result = samples.contentHashCode()
        result = 31 * result + length
        result = 31 * result + timestampMs.hashCode()
        return result
    }
}
