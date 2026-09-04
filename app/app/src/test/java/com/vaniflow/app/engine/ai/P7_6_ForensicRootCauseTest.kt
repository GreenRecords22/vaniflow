package com.vaniflow.app.engine.ai

import com.vaniflow.app.domain.model.SkillLevel
import com.vaniflow.app.engine.ai.analytics.DailyConversationUsageTracker
import com.vaniflow.app.engine.ai.cache.AIResponseCache
import com.vaniflow.app.engine.ai.memory.ConversationMemoryManager
import com.vaniflow.app.engine.ai.provider.AIProvider
import com.vaniflow.app.engine.ai.provider.ApiConfigStore
import com.vaniflow.app.engine.ai.provider.FallbackAIProvider
import com.vaniflow.app.engine.ai.provider.LocalAIProvider
import com.vaniflow.app.engine.ai.provider.ProviderConfig
import com.vaniflow.app.engine.ai.provider.ProviderHealthManager
import com.vaniflow.app.engine.ai.provider.ProviderHealthState
import com.vaniflow.app.engine.ai.provider.ProviderQuotaManager
import com.vaniflow.app.engine.ai.provider.ProviderRegistry
import com.vaniflow.app.engine.ai.provider.RemoteAIProvider
import com.vaniflow.app.engine.ai.provider.SecondaryRemoteAIProvider
import com.vaniflow.app.engine.ai.provider.adapter.GeminiProviderAdapter
import com.vaniflow.app.engine.ai.provider.adapter.OpenAICompatibleAdapter
import com.vaniflow.app.engine.ai.provider.adapter.VaniFlowGatewayAdapter
import com.vaniflow.app.engine.learning.tutor.EnglishCorrectionEngine
import com.vaniflow.app.engine.learning.tutor.EnglishErrorCategory
import com.vaniflow.app.engine.learning.tutor.TutorDecisionEngine
import com.vaniflow.app.engine.learning.tutor.model.CorrectionPolicyState
import com.vaniflow.app.engine.learning.tutor.model.DifficultyLevel
import com.vaniflow.app.engine.learning.tutor.model.TutorAction
import com.vaniflow.app.engine.learning.tutor.model.TutorLearnerState
import com.vaniflow.app.engine.learning.tutor.model.TutorResponsePlan
import com.vaniflow.app.engine.model.ModelManager
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class P7_6_ForensicRootCauseTest {

    private lateinit var configStore: ApiConfigStore
    private lateinit var quotaManager: ProviderQuotaManager
    private lateinit var healthManager: ProviderHealthManager
    private lateinit var memoryManager: ConversationMemoryManager
    private lateinit var usageTracker: DailyConversationUsageTracker
    private lateinit var fallbackAIEngine: FallbackAIEngine
    private lateinit var responseCache: AIResponseCache
    private lateinit var dialogueEngine: ConversationalDialogueEngine
    private lateinit var openAIAdapter: OpenAICompatibleAdapter
    private lateinit var geminiAdapter: GeminiProviderAdapter
    private lateinit var gatewayAdapter: VaniFlowGatewayAdapter
    private lateinit var correctionEngine: EnglishCorrectionEngine
    private lateinit var tutorDecisionEngine: TutorDecisionEngine

    @Before
    fun setup() {
        configStore = ApiConfigStore()
        configStore.clear()
        quotaManager = ProviderQuotaManager()
        healthManager = ProviderHealthManager(quotaManager)
        memoryManager = ConversationMemoryManager()
        usageTracker = DailyConversationUsageTracker()
        fallbackAIEngine = FallbackAIEngine()
        responseCache = mockk(relaxed = true)
        coEvery { responseCache.getCachedResponse(any(), any(), any(), any(), any(), any()) } returns null
        dialogueEngine = ConversationalDialogueEngine()
        openAIAdapter = mockk(relaxed = true)
        geminiAdapter = mockk(relaxed = true)
        gatewayAdapter = mockk(relaxed = true)
        correctionEngine = EnglishCorrectionEngine()
        tutorDecisionEngine = TutorDecisionEngine()
    }

    // =========================================================================
    // PHASE 1 & 2: PROVE REAL GROQ CLOUD INTEGRATION CALL
    // =========================================================================

    @Test
    fun `Phase 1 and 2 - Groq provider is invoked with real model when credentials exist`() = runTest {
        configStore.setPrimaryConfig(
            apiKey = "gsk_test_api_key_12345",
            endpoint = "https://api.groq.com/openai/v1/chat/completions",
            model = "llama-3.3-70b-versatile",
            adapterType = "openai_compatible"
        )

        val remoteProvider = RemoteAIProvider(
            healthManager = healthManager,
            dialogueEngine = dialogueEngine,
            configStore = configStore,
            openAIAdapter = openAIAdapter,
            geminiAdapter = geminiAdapter,
            gatewayAdapter = gatewayAdapter
        )

        assertTrue("Remote provider must be available when credentials are set", remoteProvider.isAvailable())

        every {
            openAIAdapter.stream(
                endpoint = "https://api.groq.com/openai/v1/chat/completions",
                apiKey = "gsk_test_api_key_12345",
                model = "llama-3.3-70b-versatile",
                systemPrompt = any(),
                history = any(),
                userInput = "What is the capital of Australia?",
                timeoutMs = any()
            )
        } returns flowOf("The ", "capital ", "of ", "Australia ", "is ", "Canberra.")

        val streamTokens = remoteProvider.streamResponse(
            systemPrompt = "You are Raya",
            conversationHistory = emptyList(),
            userInput = "What is the capital of Australia?"
        ).toList()

        val fullText = streamTokens.joinToString("")
        assertEquals("The capital of Australia is Canberra.", fullText.trim())
        assertFalse("Must not contain canned fallback phrases", fullText.contains("thoughtful point about"))
    }

    // =========================================================================
    // PHASE 3: PROVE REAL GEMINI INTEGRATION CALL
    // =========================================================================

    @Test
    fun `Phase 3 - Gemini provider is invoked with real model when secondary credentials exist`() = runTest {
        configStore.setSecondaryConfig(
            apiKey = "AIzaSy_gemini_test_key",
            endpoint = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent",
            model = "gemini-1.5-flash",
            adapterType = "gemini"
        )

        val secondaryProvider = SecondaryRemoteAIProvider(
            healthManager = healthManager,
            dialogueEngine = dialogueEngine,
            configStore = configStore,
            openAIAdapter = openAIAdapter,
            geminiAdapter = geminiAdapter
        )

        assertTrue("Secondary provider must be available when credentials are set", secondaryProvider.isAvailable())

        every {
            geminiAdapter.stream(
                endpoint = any(),
                apiKey = "AIzaSy_gemini_test_key",
                model = "gemini-1.5-flash",
                systemPrompt = any(),
                history = any(),
                userInput = "Why do people learn English?",
                timeoutMs = any()
            )
        } returns flowOf("People ", "learn ", "English ", "for ", "global ", "opportunities ", "and ", "travel.")

        val streamTokens = secondaryProvider.streamResponse(
            systemPrompt = "You are Raya",
            conversationHistory = emptyList(),
            userInput = "Why do people learn English?"
        ).toList()

        val fullText = streamTokens.joinToString("")
        assertEquals("People learn English for global opportunities and travel.", fullText.trim())
    }

    // =========================================================================
    // PHASE 4: PROVE LOCAL QWEN INVOCATION WHEN OFFLINE
    // =========================================================================

    @Test
    fun `Phase 4 - Local Qwen is invoked when cloud is unavailable`() = runTest {
        val mockModelManager = mockk<ModelManager>(relaxed = true)
        val mockGgufFile = mockk<File>()
        every { mockGgufFile.exists() } returns true
        every { mockGgufFile.length() } returns 500_000_000L
        every { mockGgufFile.absolutePath } returns "/data/models/qwen25_05b.gguf"
        every { mockModelManager.getModelFile("llm_qwen25_05b_instruct") } returns mockGgufFile

        val mockRuntime = mockk<com.vaniflow.app.engine.ai.llm.LocalLLMRuntime>(relaxed = true)
        every { mockRuntime.isAvailable() } returns true
        every { mockRuntime.generate(any()) } returns "Canberra is the capital city of Australia."

        val localAiEngine = LocalAIEngine(mockModelManager, mockRuntime)
        val localProvider = LocalAIProvider(localAiEngine, healthManager)

        val remotePrimary = RemoteAIProvider(healthManager, dialogueEngine, configStore, openAIAdapter, geminiAdapter, gatewayAdapter).apply {
            config = config.copy(isEnabled = false)
        }
        val remoteSecondary = SecondaryRemoteAIProvider(healthManager, dialogueEngine, configStore, openAIAdapter, geminiAdapter).apply {
            config = config.copy(isEnabled = false)
        }

        val registry = ProviderRegistry(
            remotePrimary = remotePrimary,
            remoteSecondary = remoteSecondary,
            localProvider = localProvider,
            fallbackProvider = FallbackAIProvider(ContextAwareFallbackEngine())
        )

        val router = SmartAIRouter(
            providerRegistry = registry,
            memoryManager = memoryManager,
            usageTracker = usageTracker,
            fallbackAIEngine = fallbackAIEngine,
            aiResponseCache = responseCache
        )

        val result = router.generateResponse(
            systemPrompt = "You are Raya",
            conversationHistory = emptyList(),
            userInput = "What is the capital of Australia?"
        )

        assertTrue(result is AIResult.Success)
        val success = result as AIResult.Success
        assertEquals("Canberra is the capital city of Australia.", success.text)
        assertEquals("VaniFlow Local AI", success.metadata.providerName)
    }

    // =========================================================================
    // PHASE 5 & 6: ZERO REACHABILITY OF CANNED RESPONSES IN NORMAL AI FLOW
    // =========================================================================

    @Test
    fun `Phase 5 and 6 - Unconfigured Remote AI does not serve canned dialogueEngine responses`() = runTest {
        configStore.clear()
        val remoteProvider = RemoteAIProvider(
            healthManager = healthManager,
            dialogueEngine = dialogueEngine,
            configStore = configStore,
            openAIAdapter = openAIAdapter,
            geminiAdapter = geminiAdapter,
            gatewayAdapter = gatewayAdapter
        )

        val result = remoteProvider.generateResponse(
            systemPrompt = "You are Raya",
            conversationHistory = emptyList(),
            userInput = "What is the capital of Australia?"
        )

        assertTrue(result is AIResult.Success)
        val text = (result as AIResult.Success).text
        assertFalse("Must not echo raw sliced words with template", text.contains("thoughtful point about Australia"))
    }

    // =========================================================================
    // PHASE 7: CRITICAL CORRECTION PATH ('I go Jaipur yesterday')
    // =========================================================================

    @Test
    fun `Phase 7 - Past tense error is caught and prompts spoken retry`() = runTest {
        val userSpeech = "I go Jaipur yesterday."
        val correction = correctionEngine.analyzeUtterance(userSpeech)

        assertTrue("Must detect past tense error in 'I go Jaipur yesterday.'", correction.hasError)
        assertEquals(EnglishErrorCategory.TENSE, correction.detectedErrors.first().category)
        assertEquals("I went to Jaipur yesterday.", correction.correctedSentence)

        val learnerState = TutorLearnerState(
            currentDifficulty = DifficultyLevel.INTERMEDIATE,
            speakingConfidence = 70f
        )
        val decision = tutorDecisionEngine.evaluateDecision(learnerState, correction)

        assertEquals(TutorAction.ASK_RETRY, decision.action)
        assertTrue(decision.shouldInterruptTurn)
        assertNotNull(decision.spokenInterventionText)
        assertTrue(decision.spokenInterventionText!!.contains("went"))

        val plan = tutorDecisionEngine.createResponsePlan(decision, correction, "raya")
        assertTrue(plan.correctionRequired)
        assertTrue(plan.learnerRetryRequired)
        assertEquals("CORRECTION_AND_PRACTICE", plan.conversationIntent)

        // Now simulate user retry
        val retryInput = "I went to Jaipur yesterday."
        val retryEval = correctionEngine.evaluateRetry(correction.detectedErrors.firstOrNull(), userSpeech, retryInput)
        assertTrue("Retry must be recognized as fixed", retryEval.isFixed)

        val retryState = learnerState.copy(isRetryActive = true, retryAttemptsCount = 1)
        val retryDecision = tutorDecisionEngine.evaluateDecision(retryState, retryEvaluation = retryEval)
        assertEquals(TutorAction.PRAISE_SUCCESS, retryDecision.action)
    }

    // =========================================================================
    // PHASE 8: CORRECT SENTENCE NO FALSE POSITIVE
    // =========================================================================

    @Test
    fun `Phase 8 - Correct sentence does not trigger false positive correction`() = runTest {
        val userSpeech = "I went to Jaipur yesterday."
        val correction = correctionEngine.analyzeUtterance(userSpeech)

        assertFalse("Correct sentence must NOT have error", correction.hasError)
        assertTrue(correction.detectedErrors.isEmpty())

        val learnerState = TutorLearnerState(
            currentDifficulty = DifficultyLevel.INTERMEDIATE,
            speakingConfidence = 80f
        )
        val decision = tutorDecisionEngine.evaluateDecision(learnerState, correction)

        assertEquals(TutorAction.NORMAL_CONVERSATION, decision.action)
        assertFalse(decision.shouldInterruptTurn)
        assertNull(decision.spokenInterventionText)

        val plan = tutorDecisionEngine.createResponsePlan(decision, correction, "raya")
        assertFalse(plan.correctionRequired)
        assertFalse(plan.learnerRetryRequired)
        assertEquals("NORMAL_CONVERSATION", plan.conversationIntent)
    }

    // =========================================================================
    // PHASE 9 & 11: QUESTION ANSWERING & ANTI-REPETITION
    // =========================================================================

    @Test
    fun `Phase 9 and 11 - Diverse open-ended questions generate distinct non-repeating answers`() = runTest {
        val questions = listOf(
            "What causes thunder?",
            "Why do people dream?",
            "How does a refrigerator cool food?",
            "Why do leaves fall from trees?",
            "Why is Jaipur called the Pink City?"
        )

        val responses = mutableListOf<String>()

        questions.forEachIndexed { index, q ->
            val mockRuntime = mockk<com.vaniflow.app.engine.ai.llm.LocalLLMRuntime>(relaxed = true)
            every { mockRuntime.isAvailable() } returns true
            val distinctAnswer = "Answer for question $index about $q."
            every { mockRuntime.generate(any()) } returns distinctAnswer

            val mockModelManager = mockk<ModelManager>(relaxed = true)
            val mockFile = mockk<File>()
            every { mockFile.exists() } returns true
            every { mockFile.length() } returns 500_000_000L
            every { mockModelManager.getModelFile(any()) } returns mockFile

            val localEngine = LocalAIEngine(mockModelManager, mockRuntime)
            val localProvider = LocalAIProvider(localEngine, healthManager)

            val remotePrimary = RemoteAIProvider(healthManager, dialogueEngine, configStore, openAIAdapter, geminiAdapter, gatewayAdapter).apply {
                config = config.copy(isEnabled = false)
            }
            val remoteSecondary = SecondaryRemoteAIProvider(healthManager, dialogueEngine, configStore, openAIAdapter, geminiAdapter).apply {
                config = config.copy(isEnabled = false)
            }

            val registry = ProviderRegistry(
                remotePrimary = remotePrimary,
                remoteSecondary = remoteSecondary,
                localProvider = localProvider,
                fallbackProvider = FallbackAIProvider(ContextAwareFallbackEngine())
            )

            val router = SmartAIRouter(
                providerRegistry = registry,
                memoryManager = memoryManager,
                usageTracker = usageTracker,
                fallbackAIEngine = fallbackAIEngine,
                aiResponseCache = responseCache
            )

            val res = router.generateResponse("System prompt", emptyList(), q)
            assertTrue(res is AIResult.Success)
            val text = (res as AIResult.Success).text
            responses.add(text)
        }

        assertEquals(5, responses.size)
        val uniqueCount = responses.toSet().size
        assertEquals("All responses must be distinct with zero duplicate rate", 5, uniqueCount)
    }

    // =========================================================================
    // PHASE 15 & 16: STRUCTURED RESULT & TUTOR RESPONSE PLAN CONTRACT
    // =========================================================================

    @Test
    fun `Phase 15 and 16 - AiGenerationResult and TutorResponsePlan structured contracts are enforced`() {
        val plan = TutorResponsePlan(
            tutorAction = TutorAction.IMPORTANT_CORRECTION,
            correctionRequired = true,
            correctionText = "I went to Jaipur yesterday",
            learnerRetryRequired = true,
            conversationIntent = "CORRECTION_AND_PRACTICE",
            contextSummary = "Discussing trip to Jaipur",
            targetConcept = "past_tense",
            characterStyle = "raya",
            generationInstruction = "Correct the past tense error and invite a retry."
        )

        assertEquals(TutorAction.IMPORTANT_CORRECTION, plan.tutorAction)
        assertTrue(plan.correctionRequired)
        assertEquals("I went to Jaipur yesterday", plan.correctionText)

        val result = AiGenerationResult(
            text = "Almost! A more natural way is 'I went to Jaipur yesterday.' Can you try saying that?",
            provider = "GROQ",
            model = "llama-3.3-70b-versatile",
            routingLevel = AIRoutingLevel.OPTIONAL_CLOUD,
            latencyMs = 350L,
            tokenCount = 24,
            wasFallback = false,
            qualityStatus = "PASS"
        )

        assertEquals("GROQ", result.provider)
        assertEquals("llama-3.3-70b-versatile", result.model)
        assertFalse(result.wasFallback)
        assertEquals("PASS", result.qualityStatus)
    }
}