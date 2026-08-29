package com.vaniflow.app.engine.audio

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mock VAD Engine for deterministic testing.
 */
@Singleton
class MockVADEngine @Inject constructor() : VADEngine {

    private val _state = MutableStateFlow(VADState.SILENCE)
    override val state: StateFlow<VADState> = _state.asStateFlow()

    private var forcedSpeech = false

    override fun processFrame(samples: ShortArray, length: Int): VADDecision {
        val nextState = if (forcedSpeech) VADState.SPEECH_CONTINUING else VADState.SILENCE
        _state.value = nextState
        return VADDecision(nextState, if (forcedSpeech) -20f else -60f, forcedSpeech)
    }

    fun setSpeechDetected(isSpeech: Boolean) {
        forcedSpeech = isSpeech
        _state.value = if (isSpeech) VADState.SPEECH_STARTED else VADState.SILENCE
    }

    override fun reset() {
        forcedSpeech = false
        _state.value = VADState.SILENCE
    }
}
