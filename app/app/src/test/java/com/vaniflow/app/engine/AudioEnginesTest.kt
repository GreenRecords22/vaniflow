package com.vaniflow.app.engine

import com.vaniflow.app.engine.stt.MockSTTEngine
import com.vaniflow.app.engine.stt.STTResult
import com.vaniflow.app.engine.tts.MockTTSEngine
import com.vaniflow.app.engine.tts.TTSResult
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioEnginesTest {

    private val sttEngine = MockSTTEngine()
    private val ttsEngine = MockTTSEngine()

    @Test
    fun testSttEngineStreamsPartialAndFinalResults() = runTest {
        assertFalse(sttEngine.isListening.value)

        val results = mutableListOf<STTResult>()
        sttEngine.startListening().collect { result ->
            results.add(result)
        }

        assertTrue("Expected partial and final results", results.isNotEmpty())
        val finalResult = results.last()
        assertTrue("Last item should be final", finalResult is STTResult.Final)
        assertTrue((finalResult as STTResult.Final).text.isNotBlank())
        assertFalse(sttEngine.isListening.value)
    }

    @Test
    fun testSttEngineCanBeCancelled() = runTest {
        val job = launch {
            sttEngine.startListening().collect {}
        }
        sttEngine.cancel()
        job.join()
        assertFalse(sttEngine.isListening.value)
    }

    @Test
    fun testTtsEngineSpeaksAndCompletes() = runTest {
        assertFalse(ttsEngine.isSpeaking.value)
        val result = ttsEngine.speak("Hello! Welcome to VaniFlow.")
        assertTrue("TTS should complete", result is TTSResult.Completed)
        assertFalse(ttsEngine.isSpeaking.value)
    }

    @Test
    fun testTtsEngineCanBeInterrupted() = runTest {
        var ttsResult: TTSResult? = null
        val job = launch {
            ttsResult = ttsEngine.speak("This is a long spoken sentence that should be interrupted by the user tapping.")
        }
        // Yield execution to allow speak coroutine to start
        kotlinx.coroutines.yield()
        ttsEngine.stop()
        job.join()

        assertEquals(TTSResult.Interrupted, ttsResult)
        assertFalse(ttsEngine.isSpeaking.value)
    }
}
