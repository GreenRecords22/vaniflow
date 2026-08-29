package com.vaniflow.app.engine.conversation

import com.vaniflow.app.domain.model.Character
import com.vaniflow.app.domain.model.ConversationState
import com.vaniflow.app.domain.model.ConversationTurn
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConversationEngineTutorLoopTest {

    private lateinit var aiEngine: AIEngine
    private lateinit var ttsEngine: TTSEngine
    private lateinit var feedbackEngine: FeedbackEngine
    private lateinit var correctionEngine: EnglishCorrectionEngine
    private lateinit var learningMemoryManager: LearningMemoryManager
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
        correctionEngine = EnglishCorrectionEngine()
        learningMemoryManager = LearningMemoryManager()

        coEvery { ttsEngine.speak(any(), any(), any()) } returns TTSResult.Completed(0)
        coEvery { ttsEngine.stop() } returns Unit
        every { aiEngine.streamResponse(any(), any(), any()) } returns flowOf("That sounds delicious!")

        engine = ConversationEngine(
            aiEngine = aiEngine,
            ttsEngine = ttsEngine,
            feedbackEngine = feedbackEngine,
            correctionEngine = correctionEngine,
            learningMemoryManager = learningMemoryManager
        )
        engine.startSession(character, scenario)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `tense mistake triggers tutor correction and enters WAITING_FOR_RETRY`() = runTest {
        launch { engine.processUserUtterance("Yesterday I buyed vegetables.") }
        dispatcher.scheduler.advanceUntilIdle()

        // Verify state is WAITING_FOR_RETRY
        assertEquals(ConversationEngine.TutorState.WAITING_FOR_RETRY, engine.tutorState)
        assertNotNull(engine.activeRetry)
        assertEquals("buyed", engine.activeRetry?.originalError?.originalText)

        // Verify AI turn contains the tutor's correction
        val turns = engine.turns.value
        val lastAiTurn = turns.lastOrNull { it.speaker == ConversationTurn.Speaker.AI }
        assertNotNull(lastAiTurn)
        assertTrue(lastAiTurn!!.text.contains("bought", ignoreCase = true))
        assertTrue(lastAiTurn.text.contains("Try saying", ignoreCase = true))
    }

    @Test
    fun `successful retry praises learner, increments mastery, and exits retry state`() = runTest {
        // Step 1: Initial mistake
        launch { engine.processUserUtterance("Yesterday I buyed vegetables.") }
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(ConversationEngine.TutorState.WAITING_FOR_RETRY, engine.tutorState)

        val initialRetries = learningMemoryManager.profile.successfulRetries

        // Step 2: Learner correctly retries
        launch { engine.processUserUtterance("Yesterday I bought vegetables.") }
        dispatcher.scheduler.advanceUntilIdle()

        // Verify retry was cleared and tutor returned to NORMAL
        assertEquals(ConversationEngine.TutorState.NORMAL, engine.tutorState)
        assertNull(engine.activeRetry)

        // Verify mastery/retries count incremented
        assertEquals(initialRetries + 1, learningMemoryManager.profile.successfulRetries)
        assertTrue(learningMemoryManager.profile.masteredConcepts.contains("past_buyed"))

        // Verify praise turn
        val turns = engine.turns.value
        val lastAiTurn = turns.lastOrNull { it.speaker == ConversationTurn.Speaker.AI }
        assertNotNull(lastAiTurn)
        assertTrue(lastAiTurn!!.text.contains("Perfect", ignoreCase = true) || lastAiTurn.text.contains("Great", ignoreCase = true))
    }

    @Test
    fun `first failed retry gives second gentle hint and stays in WAITING_FOR_RETRY`() = runTest {
        // Step 1: Initial mistake
        launch { engine.processUserUtterance("Yesterday I buyed vegetables.") }
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(ConversationEngine.TutorState.WAITING_FOR_RETRY, engine.tutorState)

        // Step 2: Incomplete retry
        launch { engine.processUserUtterance("Yesterday I still buyed vegetables.") }
        dispatcher.scheduler.advanceUntilIdle()

        // Still waiting for 2nd retry attempt
        assertEquals(ConversationEngine.TutorState.WAITING_FOR_RETRY, engine.tutorState)
        assertEquals(2, engine.activeRetry?.attemptsCount)

        val turns = engine.turns.value
        val lastAiTurn = turns.lastOrNull { it.speaker == ConversationTurn.Speaker.AI }
        assertNotNull(lastAiTurn)
        assertTrue(lastAiTurn!!.text.contains("Almost", ignoreCase = true) || lastAiTurn.text.contains("Remember", ignoreCase = true))
    }

    @Test
    fun `second failed retry reaches max attempts and resumes naturally without trapping learner`() = runTest {
        // Step 1: Initial mistake
        launch { engine.processUserUtterance("Yesterday I buyed vegetables.") }
        dispatcher.scheduler.advanceUntilIdle()

        // Step 2: Attempt 1 fail
        launch { engine.processUserUtterance("I buyed") }
        dispatcher.scheduler.advanceUntilIdle()

        // Step 3: Attempt 2 fail (exceeds limit 2)
        launch { engine.processUserUtterance("I buyed again") }
        dispatcher.scheduler.advanceUntilIdle()

        // Must exit WAITING_FOR_RETRY
        assertEquals(ConversationEngine.TutorState.NORMAL, engine.tutorState)
        assertNull(engine.activeRetry)

        val turns = engine.turns.value
        val lastAiTurn = turns.lastOrNull { it.speaker == ConversationTurn.Speaker.AI }
        assertNotNull(lastAiTurn)
        assertTrue(lastAiTurn!!.text.contains("Good try", ignoreCase = true) || lastAiTurn.text.contains("keep going", ignoreCase = true))
    }

    @Test
    fun `clean sentence does not trigger retry and proceeds directly to AI conversation`() = runTest {
        launch { engine.processUserUtterance("Yesterday I went to the market and bought fresh vegetables.") }
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(ConversationEngine.TutorState.NORMAL, engine.tutorState)
        assertNull(engine.activeRetry)

        val userTurn = engine.turns.value.firstOrNull { it.speaker == ConversationTurn.Speaker.USER }
        assertNotNull(userTurn)
        assertNull(userTurn?.correction)
    }

    @Test
    fun `subject verb agreement he dont triggers retry`() = runTest {
        launch { engine.processUserUtterance("He don't like cricket.") }
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(ConversationEngine.TutorState.WAITING_FOR_RETRY, engine.tutorState)
        assertNotNull(engine.activeRetry)
        assertEquals("he don't", engine.activeRetry?.originalError?.originalText)
    }

    @Test
    fun `minor preposition error attaches passive correction without forcing retry`() = runTest {
        launch { engine.processUserUtterance("I am good in English.") }
        dispatcher.scheduler.advanceUntilIdle()

        // Confidence-first: MINOR error does not force retry!
        assertEquals(ConversationEngine.TutorState.NORMAL, engine.tutorState)
        assertNull(engine.activeRetry)

        val userTurn = engine.turns.value.firstOrNull { it.speaker == ConversationTurn.Speaker.USER }
        assertNotNull(userTurn)
        assertNotNull(userTurn?.correction)
        assertEquals("I am good at English.", userTurn?.correction?.suggestedText)
    }

    @Test
    fun `style phrasing suggestion does not force retry`() = runTest {
        launch { engine.processUserUtterance("I very like Indian food.") }
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(ConversationEngine.TutorState.NORMAL, engine.tutorState)
        assertNull(engine.activeRetry)

        val userTurn = engine.turns.value.firstOrNull { it.speaker == ConversationTurn.Speaker.USER }
        assertNotNull(userTurn)
        assertNotNull(userTurn?.correction)
        assertEquals("I really like Indian food.", userTurn?.correction?.suggestedText)
    }
}
