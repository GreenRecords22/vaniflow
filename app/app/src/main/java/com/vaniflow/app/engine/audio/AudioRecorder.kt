package com.vaniflow.app.engine.audio

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Interface for capturing audio streams from microphone.
 */
interface AudioRecorder {
    val isRecording: StateFlow<Boolean>

    /**
     * Starts audio capture and emits raw PCM frames on an IO dispatcher.
     * Canceling collection automatically stops and releases the AudioRecord instance.
     */
    fun startStream(): Flow<AudioFrame>

    /**
     * Explicitly stop recording.
     */
    fun stopStream()
}

sealed class AudioRecorderException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class PermissionDenied(message: String = "RECORD_AUDIO permission is not granted") : AudioRecorderException(message)
    class InitializationFailed(message: String = "Failed to initialize AudioRecord device") : AudioRecorderException(message)
    class DeviceUnavailable(message: String = "Microphone is in use by another app or unavailable") : AudioRecorderException(message)
}
