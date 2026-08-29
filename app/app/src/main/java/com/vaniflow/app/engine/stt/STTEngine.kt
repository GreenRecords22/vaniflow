package com.vaniflow.app.engine.stt

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Modular Speech-To-Text Engine interface.
 */
interface STTEngine {
    val isListening: StateFlow<Boolean>

    suspend fun startListening(): Flow<STTResult>
    suspend fun stopListening()
    suspend fun cancel()
}

sealed class STTResult {
    data class Partial(val text: String) : STTResult()
    data class Final(val text: String, val confidence: Float = 0.95f) : STTResult()
    data class Error(val message: String, val cause: Throwable? = null) : STTResult()
}
