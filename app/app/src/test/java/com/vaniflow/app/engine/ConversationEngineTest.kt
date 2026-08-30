package com.vaniflow.app.engine

import com.vaniflow.app.domain.model.Character
import com.vaniflow.app.domain.model.ConversationTurn
import com.vaniflow.app.domain.model.Scenario
import com.vaniflow.app.domain.model.SkillLevel
import com.vaniflow.app.engine.ai.MockAIEngine
import com.vaniflow.app.engine.conversation.ConversationEngine
import com.vaniflow.app.domain.model.ConversationState
import com.vaniflow.app.engine.learning.MockFeedbackEngine
import com.vaniflow.app.engine.tts.MockTTSEngine
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ConversationEngineTest {

    private lateinit var conversationEngine: ConversationEngine
    private val aiEngine = MockAIEngine()
    private val ttsEngine = MockTTSEngine()
    private val feedbackEngine = MockFeedbackEngine()

    private val testCharacter = Character(
        id = "raya",
        name = "Raya",
        personality = "Friendly",
        level = SkillLevel.BEGINNER,
        voiceId = "raya_voice",
        systemPrompt = "You are Raya."
    )

    private val testScenario = Scenario(
        id = "order_coffee",
        title = "Order Coffee",
        description = "Ordering coffee",
        category = "Daily Life",
        difficulty = SkillLevel.BEGINNER,
        estimatedMinutes = 3,
        initialMessage = "Hello! What can I get for you?",
        systemPrompt = "You are a barista."
    )

    @Before
    fun setup() {
        conversationEngine = ConversationEngine(aiEngine, ttsEngine, feedbackEngine)
    }

    @Test
    fun testStartSessionCreatesInitialTurn() {
        conversationEngine.startSession(testCharacter, testScenario)
        val turns = conversationEngine.turns.value
        assertEquals(1, turns.size)
        assertEquals(ConversationTurn.Speaker.AI, turns.first().speaker)
        assertEquals(testScenario.initialMessage, turns.first().text)
    }

    @Test
    fun testProcessUserUtteranceAddsTurnsAndFeedback() = runTest {
        conversationEngine.startSession(testCharacter, testScenario)

        // Send utterance with grammar mistake
        conversationEngine.processUserUtterance("I am working in Jaipur since three years.")

        val turns = conversationEngine.turns.value
        // Initial AI turn + User turn + AI response turn = 3 turns
        assertEquals(3, turns.size)

        val userTurn = turns[1]
        assertEquals(ConversationTurn.Speaker.USER, userTurn.speaker)
        assertNotNull(userTurn.correction)
        assertTrue(userTurn.correction?.suggestedText?.contains("for three years", ignoreCase = true) == true)

        val aiTurn = turns[2]
        assertEquals(ConversationTurn.Speaker.AI, aiTurn.speaker)
        assertTrue(aiTurn.text.isNotBlank())
    }

    @Test
    fun testEndSessionGeneratesMetrics() {
        conversationEngine.startSession(testCharacter, testScenario)
        val score = conversationEngine.endSession()

        assertEquals(ConversationState.SESSION_COMPLETE, conversationEngine.state.value)
        assertTrue(score.fluencyScore in 60..100)
        assertTrue(score.grammarScore in 60..100)
        assertTrue(score.pronunciationScore in 60..100)
        assertTrue(score.vocabularyScore in 60..100)
        assertTrue(score.strongestArea.isNotBlank())
    }
}
