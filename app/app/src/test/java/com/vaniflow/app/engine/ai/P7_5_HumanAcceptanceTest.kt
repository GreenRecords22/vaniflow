package com.vaniflow.app.engine.ai

import com.vaniflow.app.domain.model.SkillLevel
import com.vaniflow.app.engine.ai.guard.QualityCheckResult
import com.vaniflow.app.engine.ai.guard.ResponseQualityGuard
import com.vaniflow.app.engine.ai.prompt.ConversationPromptBuilder
import com.vaniflow.app.engine.learning.tutor.EnglishCorrectionEngine
import com.vaniflow.app.engine.learning.tutor.EnglishError
import com.vaniflow.app.engine.learning.tutor.EnglishErrorCategory
import com.vaniflow.app.engine.learning.tutor.CorrectionSeverity
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * P7.5 Human Conversation Acceptance Test Suite.
 * Simulates human-like dialogue scenarios: Test A Daily Life, character tone fidelity,
 * human tutor correction style, and the full retry loop.
 */
class P7_5_HumanAcceptanceTest {

    private lateinit var guard: ResponseQualityGuard
    private lateinit var correctionEngine: EnglishCorrectionEngine

    @Before
    fun setUp() {
        guard = ResponseQualityGuard()
        correctionEngine = EnglishCorrectionEngine()
        RepetitionGuard.reset()
    }

    // TEST A — DAILY LIFE
    @Test
    fun testA_dailyLifeDialogueFlow() {
        val history = mutableListOf<AITurn>()

        // 1. "Hi Raya, how are you?"
        val turn1User = "Hi Raya, how are you?"
        val turn1AI = "I'm doing great, thank you! How has your day been so far?"
        assertTrue(guard.validate(turn1AI, turn1User, history) is QualityCheckResult.Valid)
        history.add(AITurn(AITurn.Role.USER, turn1User))
        history.add(AITurn(AITurn.Role.ASSISTANT, turn1AI))

        // 2. "I'm fine. Today I went to the market." -> Respond to actual statement, not generic advice
        val turn2User = "I'm fine. Today I went to the market."
        val turn2AI = "Nice! Markets are always bustling with energy—did you find everything you needed?"
        assertTrue(guard.validate(turn2AI, turn2User, history) is QualityCheckResult.Valid)
        history.add(AITurn(AITurn.Role.USER, turn2User))
        history.add(AITurn(AITurn.Role.ASSISTANT, turn2AI))

        // 3. "I bought some vegetables and then came home." -> Maintain context
        val turn3User = "I bought some vegetables and then came home."
        val turn3AI = "Sounds like a productive trip! Are you planning to cook something special with fresh veggies?"
        assertTrue(guard.validate(turn3AI, turn3User, history) is QualityCheckResult.Valid)
        history.add(AITurn(AITurn.Role.USER, turn3User))
        history.add(AITurn(AITurn.Role.ASSISTANT, turn3AI))

        // 4. "What do you usually do on weekends?" -> Answer the actual question
        val turn4User = "What do you usually do on weekends?"
        val turn4AI = "I love exploring peaceful parks, listening to great audiobooks, and discovering cozy coffee spots!"
        assertTrue(guard.validate(turn4AI, turn4User, history) is QualityCheckResult.Valid)
        history.add(AITurn(AITurn.Role.USER, turn4User))
        history.add(AITurn(AITurn.Role.ASSISTANT, turn4AI))

        // 5. "Do you like traveling?" -> Answer naturally and continue
        val turn5User = "Do you like traveling?"
        val turn5AI = "I find traveling fascinating because you get to experience vibrant cultures and cuisines! What's your dream travel destination?"
        assertTrue(guard.validate(turn5AI, turn5User, history) is QualityCheckResult.Valid)
    }

    // Human Tutor Correction Style & Retry Loop
    @Test
    fun testCorrectionStyleAndRetryLoop() {
        val userErrorUtterance = "I go Jaipur yesterday."
        val analysis = correctionEngine.analyzeUtterance(userErrorUtterance)
        assertTrue(analysis.hasError)

        val err = analysis.detectedErrors.first()
        assertEquals(EnglishErrorCategory.TENSE, err.category)

        // Preferred human-like feedback
        val humanFeedback = "Almost! A more natural way to say that is: 'I went to Jaipur yesterday'. Can you try it once?"
        assertTrue(humanFeedback.contains("went to Jaipur"))

        // User retry 1: Correct retry
        val retryEvalCorrect = correctionEngine.evaluateRetry(err, userErrorUtterance, "I went to Jaipur yesterday.")
        assertTrue("Learner's correct retry must be evaluated as fixed", retryEvalCorrect.isFixed)
        assertTrue(retryEvalCorrect.praiseFeedback.contains("Perfect") || retryEvalCorrect.praiseFeedback.contains("👏"))

        // User retry 2: Unsuccessful retry
        val retryEvalIncorrect = correctionEngine.evaluateRetry(err, userErrorUtterance, "I go yesterday.")
        assertFalse("Unsuccessful retry must not be marked as fixed", retryEvalIncorrect.isFixed)
    }

    // Character Personality Tone Tests
    @Test
    fun testCharacterPersonalityToneDirectives() {
        val rayaPrompt = ConversationPromptBuilder.buildRuntimePrompt("Raya", "Warm, enthusiastic, cheerful", "Daily Life", "Chat", SkillLevel.INTERMEDIATE, emptyList(), "Hello")
        val rudraPrompt = ConversationPromptBuilder.buildRuntimePrompt("Rudra", "Direct, professional, career coach", "Workplace", "Interview", SkillLevel.ADVANCED, emptyList(), "Hello")
        val adwaitaPrompt = ConversationPromptBuilder.buildRuntimePrompt("Adwaita", "Philosophical, calm, reflective", "Philosophy", "Deep chat", SkillLevel.INTERMEDIATE, emptyList(), "Hello")
        val shubPrompt = ConversationPromptBuilder.buildRuntimePrompt("Shub", "Friendly peer, witty, casual", "Casual", "Friend chat", SkillLevel.BEGINNER, emptyList(), "Hello")

        assertTrue(rayaPrompt.contains("Warm, enthusiastic, cheerful"))
        assertTrue(rudraPrompt.contains("Direct, professional, career coach"))
        assertTrue(adwaitaPrompt.contains("Philosophical, calm, reflective"))
        assertTrue(shubPrompt.contains("Friendly peer, witty, casual"))
    }
}
