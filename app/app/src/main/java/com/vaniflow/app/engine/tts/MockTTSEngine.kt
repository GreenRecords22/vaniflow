package com.vaniflow.app.engine.tts

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mock TTS Engine for conversation loop validation.
 * Simulates real-time speech output with sentence duration calculation
 * and immediate response to user interruption.
 */
@Singleton
class MockTTSEngine @Inject constructor() : TTSEngine {

    private val _isSpeaking = MutableStateFlow(false)
    override val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private var interrupted = false

    override suspend fun speak(text: String, voiceId: String, speed: Float): TTSResult {
        _isSpeaking.value = true
        interrupted = false

        // Calculate approximate reading duration (~150 words per minute)
        val wordCount = text.split("\\s+".toRegex()).size.coerceAtLeast(1)
        val durationMs = ((wordCount / (150f * speed)) * 60 * 1000).toLong().coerceIn(800L, 8000L)

        val stepMs = 100L
        var elapsed = 0L

        while (elapsed < durationMs) {
            if (interrupted || !_isSpeaking.value) {
                _isSpeaking.value = false
                return TTSResult.Interrupted
            }
            delay(stepMs)
            elapsed += stepMs
        }

        _isSpeaking.value = false
        return TTSResult.Completed(durationMs)
    }

    override suspend fun stop() {
        interrupted = true
        _isSpeaking.value = false
    }

    override suspend fun pause() {
        _isSpeaking.value = false
    }

    override suspend fun resume() {
        _isSpeaking.value = true
    }
}
