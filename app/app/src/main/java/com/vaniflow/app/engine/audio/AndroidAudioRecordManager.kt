package com.vaniflow.app.engine.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioRecord
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.isActive
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Production-quality AudioRecord manager implementation.
 * Captures 16 kHz 16-bit PCM mono audio from device microphone,
 * emits frames as Coroutine Flow on Dispatchers.IO,
 * and guarantees clean resource release on cancellation or lifecycle events.
 */
@Singleton
class AndroidAudioRecordManager @Inject constructor(
    @ApplicationContext private val context: Context
) : AudioRecorder {

    private val _isRecording = MutableStateFlow(false)
    override val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    @Volatile
    private var isStopRequested = false

    override fun startStream(): Flow<AudioFrame> = flow {
        // 1. Permission check
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            throw AudioRecorderException.PermissionDenied()
        }

        // 2. Buffer calculation
        val minBufferSize = AudioRecord.getMinBufferSize(
            AudioConfig.SAMPLE_RATE_HZ,
            AudioConfig.CHANNEL_CONFIG,
            AudioConfig.AUDIO_FORMAT
        )

        if (minBufferSize <= 0) {
            throw AudioRecorderException.InitializationFailed("Invalid min buffer size: $minBufferSize")
        }

        val bufferSizeInBytes = maxOf(minBufferSize * 2, AudioConfig.FRAME_SIZE_SAMPLES * AudioConfig.BYTES_PER_SAMPLE * 4)

        // 3. AudioRecord instantiation
        var audioRecord: AudioRecord? = null
        try {
            audioRecord = AudioRecord(
                AudioConfig.AUDIO_SOURCE,
                AudioConfig.SAMPLE_RATE_HZ,
                AudioConfig.CHANNEL_CONFIG,
                AudioConfig.AUDIO_FORMAT,
                bufferSizeInBytes
            )

            if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                audioRecord.release()
                throw AudioRecorderException.InitializationFailed("AudioRecord state is not INITIALIZED")
            }

            audioRecord.startRecording()
            if (audioRecord.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                audioRecord.release()
                throw AudioRecorderException.DeviceUnavailable()
            }

            _isRecording.value = true
            isStopRequested = false

            val readBuffer = ShortArray(AudioConfig.FRAME_SIZE_SAMPLES)

            // 4. Capture loop
            while (currentCoroutineContext().isActive && !isStopRequested) {
                val samplesRead = audioRecord.read(readBuffer, 0, readBuffer.size, AudioRecord.READ_BLOCKING)

                if (samplesRead > 0) {
                    val frameSamples = ShortArray(samplesRead)
                    System.arraycopy(readBuffer, 0, frameSamples, 0, samplesRead)
                    emit(AudioFrame(samples = frameSamples, length = samplesRead))
                } else if (samplesRead == AudioRecord.ERROR_INVALID_OPERATION || samplesRead == AudioRecord.ERROR_BAD_VALUE) {
                    throw AudioRecorderException.DeviceUnavailable("Error reading audio: code $samplesRead")
                }
            }
        } finally {
            // 5. Clean up safely
            _isRecording.value = false
            try {
                if (audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    audioRecord.stop()
                }
            } catch (_: Exception) {}
            try {
                audioRecord?.release()
            } catch (_: Exception) {}
        }
    }.onCompletion {
        _isRecording.value = false
    }.flowOn(Dispatchers.IO)

    override fun stopStream() {
        isStopRequested = true
        _isRecording.value = false
    }
}
