package com.vaniflow.app.engine.audio

import kotlinx.coroutines.flow.StateFlow

/**
 * Voice Activity Detection states.
 */
enum class VADState {
    SILENCE,
    SPEECH_STARTED,
    SPEECH_CONTINUING,
    SPEECH_ENDED
}

data class VADDecision(
    val state: VADState,
    val energyDbfs: Float,
    val isSpeech: Boolean
)

/**
 * Voice Activity Detection Engine interface.
 * Analyzes incoming PCM audio frames to determine user speech boundaries in real time.
 */
interface VADEngine {
    val state: StateFlow<VADState>

    /**
     * Process an incoming audio frame and return a VAD decision.
     */
    fun processFrame(samples: ShortArray, length: Int): VADDecision

    /**
     * Reset internal silence timers, noise floor estimators, and state machine.
     */
    fun reset()
}
