package com.vaniflow.app.engine.conversation

import com.vaniflow.app.domain.model.Character
import com.vaniflow.app.domain.model.ConversationState
import com.vaniflow.app.domain.model.Scenario
import com.vaniflow.app.domain.model.SkillLevel
import com.vaniflow.app.engine.ai.AIEngine
import com.vaniflow.app.engine.learning.FeedbackEngine
import com.vaniflow.app.engine.learning.tutor.EnglishCorrectionEngine
import com.vaniflow.app.engine.learning.tutor.LearningMemoryManager
import com.vaniflow.app.engine.tts.TTSEngine
import com.vaniflow.app.engine.tts.TTSResult
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TutorStateTest {

    private lateinit var aiEngine: AIEngine
    private lateinit var ttsEngine: TTSEngine
    private lateinit var feedbackEngine: FeedbackEngine
    private lateinit var engine: ConversationEngine
    private lateinit var dispatcher: TestDispatcher

    private val character = Character(
        id = "raya", name = "Raya", personality = "friendly", level = SkillLevel.BEGINNER,
        voiceId = "en_IN_raya_female", speakingRate = 1.0f, correctionStyle = "gentle",
        avatarUrl = null, systemPrompt = "sys"
    )
    private val scenario = Scenario(
        id = "daily_life", title = "Daily Life", description = "desc", category = "cat",
        difficulty = SkillLevel.BEGINNER, estimatedMinutes = 5,
        initialMessage = "Hi Alex, I'm Raya.", systemPrompt = "sys", targetVocabulary = emptyList()
    )

    @Before
    fun setup() {
        dispatcher = StandardTestDispatcher()
        Dispatchers.setMain(dispatcher)
        aiEngine = mockk(relaxed = true)
        ttsEngine = mockk(relaxed = true)
        feedbackEngine = mockk(relaxed = true)

        coEvery { ttsEngine.speak(any(), any(), any()) } returns TTSResult.Completed(0)
        coEvery { ttsEngine.stop() } returns Unit
        every { aiEngine.streamResponse(any(), any(), any()) } returns flowOf("Great!")

        engine = ConversationEngine(
            aiEngine = aiEngine,
            ttsEngine = ttsEngine,
            feedbackEngine = feedbackEngine,
            correctionEngine = EnglishCorrectionEngine(),
            learningMemoryManager = LearningMemoryManager()
        )
        engine.startSession(character, scenario)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `barge-in interruption preserves retry context for upcoming user speech`() = runTest {
        // Trigger mistake
        launch { engine.processUserUtterance("Yesterday I buyed vegetables.") }
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(ConversationEngine.TutorState.WAITING_FOR_RETRY, engine.tutorState)
        assertNotNull(engine.activeRetry)

        // User begins speaking retry
        engine.onUserStartedSpeaking()
        assertEquals(ConversationState.USER_SPEAKING, engine.state.value)

        // Active retry context is preserved
        assertEquals(ConversationEngine.TutorState.WAITING_FOR_RETRY, engine.tutorState)
        assertNotNull(engine.activeRetry)

        // Learner speaks retry after barge in
        launch { engine.processUserUtterance("Yesterday I bought vegetables.") }
        dispatcher.scheduler.advanceUntilIdle()

        // Verified evaluated and cleared
        assertEquals(ConversationEngine.TutorState.NORMAL, engine.tutorState)
        assertNull(engine.activeRetry)
    }

    @Test
    fun `startSession cleanly resets tutor state and active retry`() = runTest {
        launch { engine.processUserUtterance("Yesterday I buyed vegetables.") }
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(ConversationEngine.TutorState.WAITING_FOR_RETRY, engine.tutorState)

        // Starting a new session resets tutor state
        engine.startSession(character, scenario)
        assertEquals(ConversationEngine.TutorState.NORMAL, engine.tutorState)
        assertNull(engine.activeRetry)
    }
}
