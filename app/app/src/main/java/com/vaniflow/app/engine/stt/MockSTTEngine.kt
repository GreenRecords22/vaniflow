package com.vaniflow.app.engine.stt

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mock STT Engine for testing speech processing flows.
 * Emulates live partial speech tokens followed by a complete final sentence.
 */
@Singleton
class MockSTTEngine @Inject constructor() : STTEngine {

    private val _isListening = MutableStateFlow(false)
    override val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val mockPhrases = listOf(
        "I am working in Jaipur since three years.",
        "Could I get a large cappuccino with oat milk please?",
        "In my previous company I led the frontend architecture team.",
        "I'm planning to travel to Himachal Pradesh next weekend.",
        "Today we finalized the quarterly product roadmap with the design team."
    )

    private var phraseIndex = 0

    override suspend fun startListening(): Flow<STTResult> = flow {
        _isListening.value = true
        val phrase = mockPhrases[phraseIndex % mockPhrases.size]
        phraseIndex++

        val words = phrase.split(" ")
        val current = StringBuilder()

        // Emulate partial speech recognition
        for (word in words) {
            delay(180)
            if (!_isListening.value) return@flow // Interrupted / cancelled
            if (current.isNotEmpty()) current.append(" ")
            current.append(word)
            emit(STTResult.Partial(current.toString()))
        }

        delay(100)
        if (_isListening.value) {
            emit(STTResult.Final(phrase, confidence = 0.96f))
        }
        _isListening.value = false
    }

    override suspend fun stopListening() {
        _isListening.value = false
    }

    override suspend fun cancel() {
        _isListening.value = false
    }
}
