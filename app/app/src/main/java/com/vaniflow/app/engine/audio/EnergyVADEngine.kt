package com.vaniflow.app.engine.audio

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Robust Energy-based Voice Activity Detection (VAD) Engine.
 *
 * Implements:
 * 1. Root Mean Square (RMS) energy calculation in decibels relative to full scale (dBFS).
 * 2. Adaptive background noise floor tracking.
 * 3. Speech onset hysteresis (requires consecutive speech frames to avoid transient pop/click false positives).
 * 4. Hangover silence timeout (allows natural inter-word breathing/pauses before ending a turn).
 */
@Singleton
class EnergyVADEngine @Inject constructor() : VADEngine {

    private val _state = MutableStateFlow(VADState.SILENCE)
    override val state: StateFlow<VADState> = _state.asStateFlow()

    // Adaptive noise floor (in dBFS)
    private var noiseFloorDbfs: Float = -50.0f
    private var consecutiveSpeechFrames: Int = 0
    private var silenceAccumulatedMs: Long = 0L

    companion object {
        private const val MIN_DBFS = -90.0f
        private const val SPEECH_DELTA_DB = 10.0f
        private const val ABSOLUTE_MIN_SPEECH_DBFS = -45.0f
    }

    override fun processFrame(samples: ShortArray, length: Int): VADDecision {
        if (length <= 0) {
            return VADDecision(_state.value, MIN_DBFS, false)
        }

        // 1. Calculate RMS Energy in dBFS
        var sumSquares = 0.0
        for (i in 0 until length) {
            val sample = samples[i].toDouble()
            sumSquares += sample * sample
        }
        val rms = sqrt(sumSquares / length)
        val dbfs = if (rms > 0.0) {
            (20.0 * log10(rms / 32767.0)).toFloat().coerceIn(MIN_DBFS, 0.0f)
        } else {
            MIN_DBFS
        }

        // 2. Dynamic threshold
        val dynamicThreshold = maxOf(ABSOLUTE_MIN_SPEECH_DBFS, noiseFloorDbfs + SPEECH_DELTA_DB)
        val isFrameSpeech = dbfs >= dynamicThreshold

        val frameDurationMs = ((length.toFloat() / AudioConfig.SAMPLE_RATE_HZ) * 1000L).toLong()

        // 3. State Machine logic
        val nextState = when (_state.value) {
            VADState.SILENCE -> {
                if (isFrameSpeech) {
                    consecutiveSpeechFrames++
                    if (consecutiveSpeechFrames >= AudioConfig.SPEECH_ONSET_FRAMES) {
                        silenceAccumulatedMs = 0L
                        VADState.SPEECH_STARTED
                    } else {
                        VADState.SILENCE
                    }
                } else {
                    consecutiveSpeechFrames = 0
                    // Adapt noise floor slowly during confirmed silence
                    noiseFloorDbfs = (noiseFloorDbfs * 0.95f) + (dbfs * 0.05f)
                    VADState.SILENCE
                }
            }
            VADState.SPEECH_STARTED,
            VADState.SPEECH_CONTINUING -> {
                if (isFrameSpeech) {
                    silenceAccumulatedMs = 0L
                    VADState.SPEECH_CONTINUING
                } else {
                    silenceAccumulatedMs += frameDurationMs
                    if (silenceAccumulatedMs >= AudioConfig.SILENCE_TIMEOUT_MS) {
                        VADState.SPEECH_ENDED
                    } else {
                        // In hangover period — treat as continuing speech
                        VADState.SPEECH_CONTINUING
                    }
                }
            }
            VADState.SPEECH_ENDED -> {
                // Reset to silence after emitting SPEECH_ENDED
                consecutiveSpeechFrames = 0
                silenceAccumulatedMs = 0L
                if (isFrameSpeech) {
                    consecutiveSpeechFrames = 1
                    VADState.SILENCE
                } else {
                    VADState.SILENCE
                }
            }
        }

        _state.value = nextState
        return VADDecision(
            state = nextState,
            energyDbfs = dbfs,
            isSpeech = nextState == VADState.SPEECH_STARTED || nextState == VADState.SPEECH_CONTINUING
        )
    }

    override fun reset() {
        consecutiveSpeechFrames = 0
        silenceAccumulatedMs = 0L
        noiseFloorDbfs = -50.0f
        _state.value = VADState.SILENCE
    }
}
