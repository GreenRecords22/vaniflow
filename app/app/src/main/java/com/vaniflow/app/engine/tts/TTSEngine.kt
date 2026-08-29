package com.vaniflow.app.engine.tts

import kotlinx.coroutines.flow.StateFlow

/**
 * Modular Text-To-Speech Engine interface.
 */
interface TTSEngine {
    val isSpeaking: StateFlow<Boolean>

    suspend fun speak(text: String, voiceId: String = "default", speed: Float = 1.0f): TTSResult
    suspend fun stop()
    suspend fun pause()
    suspend fun resume()
}

sealed class TTSResult {
    data class Completed(val durationMs: Long) : TTSResult()
    data object Interrupted : TTSResult()
    data class Error(val message: String, val cause: Throwable? = null) : TTSResult()
}
