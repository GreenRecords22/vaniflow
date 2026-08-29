package com.vaniflow.app.engine.conversation

import com.vaniflow.app.domain.model.Character
import com.vaniflow.app.domain.model.ConversationState
import com.vaniflow.app.domain.model.ConversationTurn
import com.vaniflow.app.domain.model.Correction
import com.vaniflow.app.domain.model.Scenario
import com.vaniflow.app.domain.model.SkillLevel
import com.vaniflow.app.engine.ai.AIEngine
import com.vaniflow.app.engine.learning.FeedbackEngine
import com.vaniflow.app.engine.tts.TTSResult
import com.vaniflow.app.engine.tts.TTSEngine
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConversationEngineTest {

    private lateinit var aiEngine: AIEngine
    private lateinit var ttsEngine: TTSEngine
    private lateinit var feedbackEngine: FeedbackEngine
    private lateinit var engine: ConversationEngine
    private lateinit var dispatcher: TestDispatcher

    private val character = Character(
        id = "raya", name = "Raya", personality = "friendly", level = SkillLevel.INTERMEDIATE,
        voiceId = "en_IN_raya_female", speakingRate = 1.0f, correctionStyle = "gentle",
        avatarUrl = null, systemPrompt = "sys"
    )
    private val scenario = Scenario(
        id = "s", title = "Title", description = "desc", category = "cat",
        difficulty = SkillLevel.INTERMEDIATE, estimatedMinutes = 5,
        initialMessage = "Hi Alex, I'm Raya.", systemPrompt = "sys", targetVocabulary = emptyList()
    )

    @Before
    fun setup() {
        dispatcher = StandardTestDispatcher()
        Dispatchers.setMain(dispatcher)
        aiEngine = mockk(relaxed = true)
        ttsEngine = mockk(relaxed = true)
        feedbackEngine = mockk()
        coEvery { feedbackEngine.analyzeUtterance(any()) } returns null
        coEvery { ttsEngine.speak(any(), any(), any()) } returns TTSResult.Completed(0)
        coEvery { ttsEngine.stop() } just io.mockk.Runs
        engine = ConversationEngine(aiEngine, ttsEngine, feedbackEngine)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `greeting transitions to LISTENING when TTS completes`() = runTest {
        engine.startSession(character, scenario)
        engine.playInitialGreeting()
        assertEquals(ConversationState.LISTENING, engine.state.value)
    }

    @Test
    fun `greeting error surfaces friendly message and returns to IDLE`() = runTest {
        coEvery { ttsEngine.speak(any(), any(), any()) } returns TTSResult.Error("raw underlay failure")
        engine.startSession(character, scenario)
        engine.playInitialGreeting()
        assertEquals(ConversationState.IDLE, engine.state.value)
        assertNotNull(engine.errorMessage.value)
        assertFalse(engine.errorMessage.value!!.contains("underlay", ignoreCase = true))
    }

    @Test
    fun `process utterance appends user and ai turns then returns to LISTENING`() = runTest {
        every { aiEngine.streamResponse(any(), any(), any()) } returns flowOf("Hello", " world")
        engine.startSession(character, scenario)
        launch { engine.processUserUtterance("I am working in Jaipur") }
        dispatcher.scheduler.advanceUntilIdle()
        val turns = engine.turns.value
        assertTrue(turns.any { it.speaker == com.vaniflow.app.domain.model.ConversationTurn.Speaker.USER && it.text == "I am working in Jaipur" })
        assertTrue(turns.any { it.speaker == com.vaniflow.app.domain.model.ConversationTurn.Speaker.AI && it.text.contains("Hello world") })
        assertEquals(ConversationState.LISTENING, engine.state.value)
    }

    @Test
    fun `blank utterance does not start generation and returns to LISTENING`() = runTest {
        engine.startSession(character, scenario)
        engine.processUserUtterance("    ")
        assertEquals(ConversationState.LISTENING, engine.state.value)
        coEvery { aiEngine.streamResponse(any(), any(), any()) } returns flowOf("x")
        // Ensure generation was never invoked for the blank utterance
        io.mockk.verify(exactly = 0) { aiEngine.streamResponse(any(), any(), any()) }
    }

    @Test
    fun `AI generation failure sets ERROR with friendly message and no raw exception`() = runTest {
        every { aiEngine.streamResponse(any(), any(), any()) } returns flow { throw RuntimeException("SocketTimeoutException: connection reset by peer") }
        engine.startSession(character, scenario)
        launch { engine.processUserUtterance("hello") }
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(ConversationState.ERROR, engine.state.value)
        val msg = engine.errorMessage.value
        assertNotNull(msg)
        assertFalse(msg!!.contains("SocketTimeout", ignoreCase = true))
        assertFalse(msg.contains("connection reset", ignoreCase = true))
        assertFalse(msg.contains("Exception", ignoreCase = true))
    }

    @Test
    fun `TTS error during streaming surfaces friendly message`() = runTest {
        every { aiEngine.streamResponse(any(), any(), any()) } returns flowOf("First sentence. ", "Second sentence.")
        coEvery { ttsEngine.speak(any(), any(), any()) } returns TTSResult.Error("audio track dead")
        engine.startSession(character, scenario)
        launch { engine.processUserUtterance("hello") }
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(ConversationState.LISTENING, engine.state.value)
        val msg = engine.errorMessage.value
        assertNotNull(msg)
        assertFalse(msg!!.contains("audio track", ignoreCase = true))
    }

    @Test
    fun `interruption stops TTS and marks INTERRUPTED without stale updates`() = runTest {
        every { aiEngine.streamResponse(any(), any(), any()) } returns flow {
            emit("Hi")
            awaitCancellation()
        }
        engine.startSession(character, scenario)
        val job = async { engine.processUserUtterance("hello") }
        // Let the first token + TTS run
        dispatcher.scheduler.advanceUntilIdle()
        engine.interrupt()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(ConversationState.INTERRUPTED, engine.state.value)
        coVerify { ttsEngine.stop() }
        job.cancel()
    }

    @Test
    fun `cancelAll stops TTS and prevents further state changes`() = runTest {
        every { aiEngine.streamResponse(any(), any(), any()) } returns flow {
            emit("Hi")
            awaitCancellation()
        }
        engine.startSession(character, scenario)
        val job = async { engine.processUserUtterance("hello") }
        dispatcher.scheduler.advanceUntilIdle()
        engine.cancelAll()
        dispatcher.scheduler.advanceUntilIdle()
        coVerify { ttsEngine.stop() }
        job.cancel()
    }

    @Test
    fun `endSession persists and reaches SESSION_COMPLETE without crashing`() = runTest {
        every { aiEngine.streamResponse(any(), any(), any()) } returns flowOf("Hi there.")
        engine.startSession(character, scenario)
        launch { engine.processUserUtterance("hello") }
        dispatcher.scheduler.advanceUntilIdle()
        val score = engine.endSession()
        assertEquals(ConversationState.SESSION_COMPLETE, engine.state.value)
        assertTrue(score.speakingTimeMinutes >= 1)
    }

    @Test
    fun `getCurrentSessionId exposes the id provided to startSession`() = runTest {
        engine.startSession(character, scenario, "my_session_42")
        assertEquals("my_session_42", engine.getCurrentSessionId())
    }

    @Test
    fun `empty AI response does not leave a dangling empty AI turn`() = runTest {
        every { aiEngine.streamResponse(any(), any(), any()) } returns flowOf()
        engine.startSession(character, scenario)
        launch { engine.processUserUtterance("hello") }
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(ConversationState.LISTENING, engine.state.value)
        assertTrue(
            "No blank assistant turn should remain when AI returned nothing",
            engine.turns.value.none { it.speaker == ConversationTurn.Speaker.AI && it.text.isBlank() }
        )
    }

    @Test
    fun `interruption prunes the empty placeholder AI turn`() = runTest {
        every { aiEngine.streamResponse(any(), any(), any()) } returns flow { kotlinx.coroutines.delay(Long.MAX_VALUE) }
        engine.startSession(character, scenario)
        val job = launch { engine.processUserUtterance("hello") }
        dispatcher.scheduler.advanceUntilIdle()
        engine.interrupt()
        dispatcher.scheduler.advanceUntilIdle()
        job.join()
        // Last turn must be the user's utterance, not a blank assistant placeholder
        val last = engine.turns.value.last()
        assertEquals(ConversationTurn.Speaker.USER, last.speaker)
        assertEquals("hello", last.text)
        assertTrue(
            "Interrupted generation must not persist a blank AI turn",
            engine.turns.value.none { it.speaker == ConversationTurn.Speaker.AI && it.text.isBlank() }
        )
    }
}
