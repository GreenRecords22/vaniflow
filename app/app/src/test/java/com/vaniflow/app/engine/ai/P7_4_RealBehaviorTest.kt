package com.vaniflow.app.engine.ai

import com.vaniflow.app.domain.model.SkillLevel
import com.vaniflow.app.engine.ai.guard.QualityCheckResult
import com.vaniflow.app.engine.ai.guard.QualityFailureType
import com.vaniflow.app.engine.ai.guard.ResponseQualityGuard
import com.vaniflow.app.engine.ai.prompt.ConversationPromptBuilder
import com.vaniflow.app.engine.learning.tutor.model.TutorAction
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * P7.4 Real Conversational AI + Response Quality Guard Unit Test Suite.
 * 42+ granular behavioral verification tests.
 */
class P7_4_RealBehaviorTest {

    private lateinit var guard: ResponseQualityGuard
    private lateinit var fallbackEngine: FallbackAIEngine

    @Before
    fun setUp() {
        guard = ResponseQualityGuard()
        fallbackEngine = FallbackAIEngine()
        RepetitionGuard.reset()
    }

    // 1. EMPTY & SHORT RESPONSE CHECKS
    @Test
    fun test01_emptyResponseRejected() {
        val res = guard.validate("", "Hello!")
        assertTrue(res is QualityCheckResult.Invalid)
        assertEquals(QualityFailureType.EMPTY_RESPONSE, (res as QualityCheckResult.Invalid).failureType)
    }

    @Test
    fun test02_whitespaceOnlyRejected() {
        val res = guard.validate("   \n  \t ", "How are you?")
        assertTrue(res is QualityCheckResult.Invalid)
        assertEquals(QualityFailureType.EMPTY_RESPONSE, (res as QualityCheckResult.Invalid).failureType)
    }

    @Test
    fun test03_tooShortRejected() {
        val res = guard.validate("k", "What is your name?")
        assertTrue(res is QualityCheckResult.Invalid)
        assertEquals(QualityFailureType.TOO_SHORT, (res as QualityCheckResult.Invalid).failureType)
    }

    @Test
    fun test04_validShortReplyAccepted() {
        val res = guard.validate("Of course.", "Can you help me?")
        assertTrue(res is QualityCheckResult.Valid)
    }

    // 2. SYSTEM PROMPT LEAK CHECKS
    @Test
    fun test05_systemPromptLeakDetected_userSpeechTag() {
        val res = guard.validate("<user_speech>I love pizza</user_speech>", "I love pizza")
        assertTrue(res is QualityCheckResult.Invalid)
        assertEquals(QualityFailureType.SYSTEM_PROMPT_LEAK, (res as QualityCheckResult.Invalid).failureType)
    }

    @Test
    fun test06_systemPromptLeakDetected_constitutionHeader() {
        val res = guard.validate("[VANIFLOW TUTOR CONSTITUTION v1.0] Speak naturally", "Hello")
        assertTrue(res is QualityCheckResult.Invalid)
        assertEquals(QualityFailureType.SYSTEM_PROMPT_LEAK, (res as QualityCheckResult.Invalid).failureType)
    }

    @Test
    fun test07_systemPromptLeakDetected_apiKey() {
        val res = guard.validate("Bearer sk-test-key-12345678", "Who are you?")
        assertTrue(res is QualityCheckResult.Invalid)
        assertEquals(QualityFailureType.SYSTEM_PROMPT_LEAK, (res as QualityCheckResult.Invalid).failureType)
    }

    // 3. INTERNAL REASONING LEAK CHECKS
    @Test
    fun test08_reasoningLeakDetected_thinkTag() {
        val res = guard.validate("<think>The student made a grammar mistake here.</think> Good job!", "I go school")
        assertTrue(res is QualityCheckResult.Invalid)
        assertEquals(QualityFailureType.INTERNAL_REASONING_LEAK, (res as QualityCheckResult.Invalid).failureType)
    }

    @Test
    fun test09_reasoningLeakDetected_bracketReasoning() {
        val res = guard.validate("[REASONING] User asked about coffee [/REASONING] I love espresso!", "Do you like coffee?")
        assertTrue(res is QualityCheckResult.Invalid)
        assertEquals(QualityFailureType.INTERNAL_REASONING_LEAK, (res as QualityCheckResult.Invalid).failureType)
    }

    // 4. META AI CHECKS
    @Test
    fun test10_metaAiDetected_aiLanguageModel() {
        val res = guard.validate("As an AI language model, I do not drink coffee.", "Do you like coffee?")
        assertTrue(res is QualityCheckResult.Invalid)
        assertEquals(QualityFailureType.META_AI_RESPONSE, (res as QualityCheckResult.Invalid).failureType)
    }

    @Test
    fun test11_metaAiDetected_noFeelingsAsAi() {
        val res = guard.validate("I don't have feelings as an AI, but that sounds nice.", "Are you happy?")
        assertTrue(res is QualityCheckResult.Invalid)
        assertEquals(QualityFailureType.META_AI_RESPONSE, (res as QualityCheckResult.Invalid).failureType)
    }

    // 5. GENERIC FILLER CHECKS
    @Test
    fun test12_genericFillerDetected_thatsInteresting() {
        val res = guard.validate("That's interesting! Keep practicing your English!", "I went to Paris last summer.")
        assertTrue(res is QualityCheckResult.Invalid)
        assertEquals(QualityFailureType.GENERIC_FILLER, (res as QualityCheckResult.Invalid).failureType)
    }

    @Test
    fun test13_genericFillerDetected_keepPracticing() {
        val res = guard.validate("English is a journey. Keep practicing every day.", "What is the weather like?")
        assertTrue(res is QualityCheckResult.Invalid)
        assertEquals(QualityFailureType.GENERIC_FILLER, (res as QualityCheckResult.Invalid).failureType)
    }

    // 6. EXCESSIVE PRAISE & UNNATURAL LOOPS
    @Test
    fun test14_excessivePraiseDetected() {
        val res = guard.validate("Awesome! Super! Amazing! Brilliant!", "I ate an apple.")
        assertTrue(res is QualityCheckResult.Invalid)
        assertEquals(QualityFailureType.EXCESSIVE_PRAISE, (res as QualityCheckResult.Invalid).failureType)
    }

    @Test
    fun test15_unnaturalLoopsDetected() {
        val res = guard.validate("the the the the the the dog is here????", "What do you see?")
        assertTrue(res is QualityCheckResult.Invalid)
        assertEquals(QualityFailureType.UNNATURAL_RESPONSE, (res as QualityCheckResult.Invalid).failureType)
    }

    // 7. REPETITION & NEAR-DUPLICATE CHECKS
    @Test
    fun test16_exactRepetitionDetected() {
        val history = listOf(
            AITurn(AITurn.Role.USER, "Hello"),
            AITurn(AITurn.Role.ASSISTANT, "I love exploring new coffee shops on weekends!")
        )
        val res = guard.validate("I love exploring new coffee shops on weekends!", "What do you do?", history)
        assertTrue(res is QualityCheckResult.Invalid)
        assertEquals(QualityFailureType.REPETITION, (res as QualityCheckResult.Invalid).failureType)
    }

    @Test
    fun test17_jaccardNearDuplicateDetected() {
        val history = listOf(
            AITurn(AITurn.Role.USER, "Tell me about weekends"),
            AITurn(AITurn.Role.ASSISTANT, "I really love exploring new coffee shops on sunny weekends with friends")
        )
        val candidate = "I love exploring new coffee shops on sunny weekends with good friends!"
        val res = guard.validate(candidate, "Tell me more", history)
        assertTrue(res is QualityCheckResult.Invalid)
        assertEquals(QualityFailureType.NEAR_DUPLICATE, (res as QualityCheckResult.Invalid).failureType)
    }

    @Test
    fun test18_duplicateOpeningDetected() {
        val history = listOf(
            AITurn(AITurn.Role.USER, "I like dogs"),
            AITurn(AITurn.Role.ASSISTANT, "That is a great choice for pets.")
        )
        val candidate = "That is a great perspective on nature."
        val res = guard.validate(candidate, "I also like trees", history)
        assertTrue(res is QualityCheckResult.Invalid)
        assertEquals(QualityFailureType.DUPLICATE_OPENING, (res as QualityCheckResult.Invalid).failureType)
    }

    // 8. QUESTION ANSWERING & INTENT MATCHING
    @Test
    fun test19_directQuestionNotAnsweredDetected() {
        val res = guard.validate("What about you?", "What is your favorite dish?")
        assertTrue(res is QualityCheckResult.Invalid)
        assertEquals(QualityFailureType.QUESTION_IGNORED, (res as QualityCheckResult.Invalid).failureType)
    }

    @Test
    fun test20_directQuestionAnsweredAccepted() {
        val res = guard.validate("I absolutely love homemade pasta! What about you?", "What is your favorite dish?")
        assertTrue(res is QualityCheckResult.Valid)
    }

    @Test
    fun test21_contextIgnoredResetDetected() {
        val history = listOf(
            AITurn(AITurn.Role.USER, "I moved to Tokyo"),
            AITurn(AITurn.Role.ASSISTANT, "Tokyo is an exciting city! How is your apartment?")
        )
        val res = guard.validate("Hello! Welcome to English practice! What would you like to talk about today?", "Why is that?", history)
        assertTrue(res is QualityCheckResult.Invalid)
        assertEquals(QualityFailureType.CONTEXT_IGNORED, (res as QualityCheckResult.Invalid).failureType)
    }

    @Test
    fun test22_contextContinuityAccepted() {
        val history = listOf(
            AITurn(AITurn.Role.USER, "I moved to Tokyo"),
            AITurn(AITurn.Role.ASSISTANT, "Tokyo is an exciting city!")
        )
        val res = guard.validate("Because the transit system and food culture are world-class!", "Why is that?", history)
        assertTrue(res is QualityCheckResult.Valid)
    }

    @Test
    fun test23_userIntentMismatch_definitionIgnoredDetected() {
        val res = guard.validate("That's great! How was your weekend?", "What does serendipity mean?")
        assertTrue(res is QualityCheckResult.Invalid)
        assertEquals(QualityFailureType.USER_INTENT_MISMATCH, (res as QualityCheckResult.Invalid).failureType)
    }

    @Test
    fun test24_userIntentMatched_definitionProvidedAccepted() {
        val res = guard.validate("Serendipity means finding something good without looking for it.", "What does serendipity mean?")
        assertTrue(res is QualityCheckResult.Valid)
    }

    // 9. TUTOR ACTION VIOLATION CHECKS
    @Test
    fun test25_tutorActionViolation_missingCorrectionDetected() {
        val res = guard.validate(
            candidate = "That is very nice.",
            userInput = "I go to cinema yesterday.",
            tutorAction = TutorAction.ASK_RETRY
        )
        assertTrue(res is QualityCheckResult.Invalid)
        assertEquals(QualityFailureType.TUTOR_ACTION_VIOLATION, (res as QualityCheckResult.Invalid).failureType)
    }

    @Test
    fun test26_tutorActionExecuted_correctionPresentAccepted() {
        val res = guard.validate(
            candidate = "Nice! For the past, try saying 'I went to the cinema yesterday'. Give it a try!",
            userInput = "I go to cinema yesterday.",
            tutorAction = TutorAction.ASK_RETRY
        )
        assertTrue(res is QualityCheckResult.Valid)
    }

    @Test
    fun test27_unsafeContentDetected() {
        val res = guard.validate("Here is the hate speech test marker content.", "Tell me something")
        assertTrue(res is QualityCheckResult.Invalid)
        assertEquals(QualityFailureType.UNSAFE_CONTENT, (res as QualityCheckResult.Invalid).failureType)
    }

    // 10. PREFIX CLEANING & SANITIZATION
    @Test
    fun test28_cleanPrefixes_characterNamePrefix() {
        val clean = guard.cleanPrefixes("Raya: Good morning!", "Raya")
        assertEquals("Good morning!", clean)
    }

    @Test
    fun test29_cleanPrefixes_assistantPrefix() {
        val clean = guard.cleanPrefixes("Assistant: That sounds delicious.", "Raya")
        assertEquals("That sounds delicious.", clean)
    }

    @Test
    fun test30_cleanPrefixes_systemPrefix() {
        val clean = guard.cleanPrefixes("System : Here is the response.", "Raya")
        assertEquals("Here is the response.", clean)
    }

    @Test
    fun test31_sanitizeOrFallback_safeCleanedReturned() {
        val out = guard.sanitizeOrFallback("Raya: I love reading books.", "What do you like?", "Raya", "Fallback")
        assertEquals("I love reading books.", out)
    }

    @Test
    fun test32_sanitizeOrFallback_fillerReturnsFallback() {
        val out = guard.sanitizeOrFallback("That's interesting! Keep practicing your English!", "Hello", "Raya", "Fallback text")
        assertEquals("Fallback text", out)
    }

    // 11. FALLBACK ENGINE HARDENING
    @Test
    fun test33_fallbackEngine_returnsEmergencyFallbackRoutingLevel() = runBlocking {
        val result = fallbackEngine.generateResponse("SYSTEM", emptyList(), "Hello")
        assertTrue(result is AIResult.Success)
        val success = result as AIResult.Success
        assertEquals(AIRoutingLevel.EMERGENCY_FALLBACK, success.metadata.routingLevel)
    }

    @Test
    fun test34_fallbackEngine_returnsCorrectProviderName() = runBlocking {
        val result = fallbackEngine.generateResponse("SYSTEM", emptyList(), "Hello")
        assertTrue(result is AIResult.Success)
        val success = result as AIResult.Success
        assertEquals("VaniFlow Emergency Fallback", success.metadata.providerName)
    }

    @Test
    fun test35_fallbackEngine_noCannedKeywordTrees() = runBlocking {
        val result = fallbackEngine.generateResponse("SYSTEM", emptyList(), "I love cooking pasta and making pizza with cheese")
        assertTrue(result is AIResult.Success)
        val success = result as AIResult.Success
        assertFalse(success.text.contains("That's interesting! Keep practicing"))
        assertTrue(success.text.isNotBlank())
    }

    // 12. PROMPT BUILDER VERIFICATION
    @Test
    fun test36_promptBuilder_containsTutorConstitution() {
        val prompt = ConversationPromptBuilder.buildRuntimePrompt(
            characterName = "Raya",
            personalityPrompt = "Energetic and cheerful",
            scenarioTitle = "Coffee Shop",
            scenarioPrompt = "Order coffee",
            userLevel = SkillLevel.INTERMEDIATE,
            history = emptyList(),
            userInput = "Can I have a latte?"
        )
        assertTrue(prompt.contains("VANIFLOW TUTOR CONSTITUTION"))
    }

    @Test
    fun test37_promptBuilder_containsDirectAnswerRule() {
        val prompt = ConversationPromptBuilder.buildRuntimePrompt(
            characterName = "Raya",
            personalityPrompt = "Friendly",
            scenarioTitle = "Daily Life",
            scenarioPrompt = "General chat",
            userLevel = SkillLevel.BEGINNER,
            history = emptyList(),
            userInput = "What is your hobby?"
        )
        assertTrue(prompt.contains("answer the actual question in character first"))
    }

    @Test
    fun test38_promptBuilder_containsPronounResolutionRule() {
        val prompt = ConversationPromptBuilder.buildRuntimePrompt(
            characterName = "Raya",
            personalityPrompt = "Friendly",
            scenarioTitle = "Daily Life",
            scenarioPrompt = "General chat",
            userLevel = SkillLevel.BEGINNER,
            history = emptyList(),
            userInput = "Why is it good?"
        )
        assertTrue(prompt.contains("resolve them from the CONVERSATION HISTORY"))
    }

    @Test
    fun test39_promptBuilder_correctiveRegenerationPromptFormatting() {
        val base = "SYSTEM: you are Raya."
        val corrective = ConversationPromptBuilder.buildCorrectiveRegenerationPrompt(
            basePrompt = base,
            failureReason = "Response is generic canned filler",
            correctiveGuidance = "Address the user's specific hobby."
        )
        assertTrue(corrective.contains("CRITICAL CORRECTION FOR THIS ATTEMPT"))
        assertTrue(corrective.contains("Response is generic canned filler"))
        assertTrue(corrective.contains("Address the user's specific hobby."))
    }
}
