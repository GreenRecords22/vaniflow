package com.vaniflow.app.engine

import android.content.Context
import com.vaniflow.app.engine.tts.CharacterVoiceRegistry
import com.vaniflow.app.engine.tts.RealOfflineTTSEngine
import com.vaniflow.app.engine.tts.TTSResult
import io.mockk.mockk
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RealOfflineTTSEngineTest {

    private lateinit var mockContext: Context
    private lateinit var voiceRegistry: CharacterVoiceRegistry
    private lateinit var ttsEngine: RealOfflineTTSEngine

    @Before
    fun setup() {
        mockContext = mockk(relaxed = true)
        voiceRegistry = CharacterVoiceRegistry()
        ttsEngine = RealOfflineTTSEngine(mockContext, voiceRegistry)
    }

    @Test
    fun testEmptyTextCompletesImmediately() = runTest {
        val result = ttsEngine.speak("", "en_IN_raya_warm")
        assertTrue(result is TTSResult.Completed)
        assertEquals(0L, (result as TTSResult.Completed).durationMs)
        assertFalse(ttsEngine.isSpeaking.value)
    }

    @Test
    fun testSingleSentenceSynthesisCompletes() = runTest {
        val result = ttsEngine.speak("Hello! Welcome to VaniFlow.", "en_IN_raya_warm")
        assertTrue("Expected completed TTS result", result is TTSResult.Completed)
        assertTrue((result as TTSResult.Completed).durationMs >= 0)
        assertFalse(ttsEngine.isSpeaking.value)
    }

    @Test
    fun testMultiSentenceSynthesisCompletes() = runTest {
        val text = "Good morning! Welcome to your English speaking practice. What would you like to discuss today?"
        val result = ttsEngine.speak(text, "en_IN_raya_warm")
        assertTrue("Multi-sentence TTS should complete", result is TTSResult.Completed)
        assertFalse(ttsEngine.isSpeaking.value)
    }

    @Test
    fun testInstantInterruptionStopsPlayback() = runTest {
        var ttsResult: TTSResult? = null
        val longText = "This is a very long conversational response that should take several seconds to read completely and will be immediately interrupted."

        val job = launch {
            ttsResult = ttsEngine.speak(longText, "en_IN_raya_warm")
        }

        kotlinx.coroutines.delay(40)
        ttsEngine.stop()
        job.join()

        assertEquals(TTSResult.Interrupted, ttsResult)
        assertFalse(ttsEngine.isSpeaking.value)
    }
}
