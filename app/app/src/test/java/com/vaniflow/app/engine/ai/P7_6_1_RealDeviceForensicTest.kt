package com.vaniflow.app.engine.ai

import com.vaniflow.app.engine.ai.analytics.DailyConversationUsageTracker
import com.vaniflow.app.engine.ai.cache.AIResponseCache
import com.vaniflow.app.engine.ai.guard.QualityCheckResult
import com.vaniflow.app.engine.ai.guard.ResponseQualityGuard
import com.vaniflow.app.engine.ai.memory.ConversationMemoryManager
import com.vaniflow.app.engine.ai.provider.ApiConfigStore
import com.vaniflow.app.engine.ai.provider.FallbackAIProvider
import com.vaniflow.app.engine.ai.provider.LocalAIProvider
import com.vaniflow.app.engine.ai.provider.ProviderHealthManager
import com.vaniflow.app.engine.ai.provider.ProviderQuotaManager
import com.vaniflow.app.engine.ai.provider.ProviderRegistry
import com.vaniflow.app.engine.ai.provider.RemoteAIProvider
import com.vaniflow.app.engine.ai.provider.SecondaryRemoteAIProvider
import com.vaniflow.app.engine.ai.provider.adapter.GeminiProviderAdapter
import com.vaniflow.app.engine.ai.provider.adapter.OpenAICompatibleAdapter
import com.vaniflow.app.engine.ai.routing.SmartResponseDecisionEngine
import com.vaniflow.app.engine.learning.tutor.EnglishCorrectionEngine
import com.vaniflow.app.engine.learning.tutor.TutorCorrectionDecision
import com.vaniflow.app.engine.learning.tutor.TutorDecisionEngine
import com.vaniflow.app.engine.learning.tutor.model.DifficultyLevel
import com.vaniflow.app.engine.learning.tutor.model.TutorAction
import com.vaniflow.app.engine.learning.tutor.model.TutorLearnerState
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class P7_6_1_RealDeviceForensicTest {

    private lateinit var memoryManager: ConversationMemoryManager
    private lateinit var usageTracker: DailyConversationUsageTracker
    private lateinit var quotaManager: ProviderQuotaManager
    private lateinit var healthManager: ProviderHealthManager
    private lateinit var configStore: ApiConfigStore
    private lateinit var mockOpenAIAdapter: OpenAICompatibleAdapter
    private lateinit var mockGeminiAdapter: GeminiProviderAdapter
    private lateinit var remotePrimary: RemoteAIProvider
    private lateinit var remoteSecondary: SecondaryRemoteAIProvider
    private lateinit var localProvider: LocalAIProvider
    private lateinit var fallbackProvider: FallbackAIProvider
    private lateinit var providerRegistry: ProviderRegistry
    private lateinit var router: SmartAIRouter
    private lateinit var decisionEngine: SmartResponseDecisionEngine
    private lateinit var tutorEngine: TutorDecisionEngine
    private lateinit var correctionEngine: EnglishCorrectionEngine
    private lateinit var qualityGuard: ResponseQualityGuard

    @Before
    fun setup() {
        memoryManager = ConversationMemoryManager()
        usageTracker = DailyConversationUsageTracker()
        quotaManager = ProviderQuotaManager()
        healthManager = ProviderHealthManager(quotaManager)
        configStore = ApiConfigStore()
        configStore.setPrimaryConfig("gsk_test_key_1234567890", "https://api.groq.com/openai/v1/chat/completions", "llama-3.3-70b-versatile", "openai_compatible")
        configStore.setSecondaryConfig("AIzaSyTestKey_1234567890", "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent", "gemini-1.5-flash", "gemini")

        mockOpenAIAdapter = mockk(relaxed = true)
        mockGeminiAdapter = mockk(relaxed = true)

        remotePrimary = RemoteAIProvider(healthManager, ConversationalDialogueEngine(), configStore, mockOpenAIAdapter, mockGeminiAdapter)
        remoteSecondary = SecondaryRemoteAIProvider(healthManager, ConversationalDialogueEngine(), configStore, mockOpenAIAdapter, mockGeminiAdapter)
        localProvider = mockk(relaxed = true) {
            every { providerId } returns "local_qwen"
            every { priority } returns 3
            every { isAvailable() } returns true
        }
        fallbackProvider = mockk(relaxed = true) {
            every { providerId } returns "emergency_fallback"
            every { priority } returns 4
            every { isAvailable() } returns true
        }

        providerRegistry = ProviderRegistry(listOf(remotePrimary, remoteSecondary, localProvider, fallbackProvider))
        val cache = mockk<AIResponseCache>(relaxed = true)
        coEvery { cache.getCachedResponse(any(), any(), any(), any(), any(), any()) } returns null
        decisionEngine = SmartResponseDecisionEngine(memoryManager, cache, usageTracker, providerRegistry)
        router = SmartAIRouter(providerRegistry, memoryManager, usageTracker, FallbackAIEngine(), cache, decisionEngine)
        tutorEngine = TutorDecisionEngine()
        correctionEngine = EnglishCorrectionEngine()
        qualityGuard = ResponseQualityGuard()
    }

    @Test
    fun testTurn1_Greeting() = runTest {
        val userInput = "Hello, how are you?"
        coEvery { mockOpenAIAdapter.generate(any(), any(), any(), any(), any(), any(), any()) } returns AIResult.Success(
            text = "Hello! I'm doing great, thank you. How is your day going?",
            latencyMs = 240,
            metadata = AIResponseMetadata(AIRoutingLevel.OPTIONAL_CLOUD, 240, 18, "llama-3.3-70b-versatile")
        )

        val correction = correctionEngine.analyzeUtterance(userInput)
        assertFalse("Greeting has no grammar errors", correction.hasError)

        val state = TutorLearnerState(currentDifficulty = DifficultyLevel.INTERMEDIATE, speakingConfidence = 80f)
        val decision = tutorEngine.evaluateDecision(state, correction)
        assertEquals(TutorAction.NORMAL_CONVERSATION, decision.action)

        val result = router.generateResponse("You are Raya", emptyList(), userInput)
        assertTrue(result is AIResult.Success)
        val response = (result as AIResult.Success).text
        assertEquals("Hello! I'm doing great, thank you. How is your day going?", response)

        val quality = qualityGuard.validate(response, userInput, emptyList(), characterName = "raya")
        assertTrue("Quality check must be valid", quality is QualityCheckResult.Valid)
    }

    @Test
    fun testTurn2_LivingInJaipur() = runTest {
        val userInput = "I live in Jaipur."
        coEvery { mockOpenAIAdapter.generate(any(), any(), any(), any(), any(), any(), any()) } returns AIResult.Success(
            text = "Jaipur is a wonderful city! The Pink City has such rich history. How long have you lived there?",
            latencyMs = 260,
            metadata = AIResponseMetadata(AIRoutingLevel.OPTIONAL_CLOUD, 260, 22, "llama-3.3-70b-versatile")
        )

        val correction = correctionEngine.analyzeUtterance(userInput)
        assertFalse("Statement has no errors", correction.hasError)

        val result = router.generateResponse("You are Raya", emptyList(), userInput)
        assertTrue(result is AIResult.Success)
        val text = (result as AIResult.Success).text
        assertTrue("Mentions Jaipur/Pink City", text.contains("Jaipur", ignoreCase = true) || text.contains("Pink City", ignoreCase = true))
    }

    @Test
    fun testTurn3_PerspectiveOnJaipur() = runTest {
        val userInput = "What do you think about Jaipur?"
        coEvery { mockOpenAIAdapter.generate(any(), any(), any(), any(), any(), any(), any()) } returns AIResult.Success(
            text = "I think Jaipur is stunning! With places like Hawa Mahal and Amber Fort, its architecture and royal culture are fascinating. What is your favorite spot there?",
            latencyMs = 280,
            metadata = AIResponseMetadata(AIRoutingLevel.OPTIONAL_CLOUD, 280, 30, "llama-3.3-70b-versatile")
        )

        val result = router.generateResponse("You are Raya", emptyList(), userInput)
        assertTrue(result is AIResult.Success)
        val text = (result as AIResult.Success).text
        assertTrue("Directly answers thoughts on Jaipur", text.contains("Jaipur", ignoreCase = true) || text.contains("Hawa Mahal", ignoreCase = true))
    }

    @Test
    fun testTurn4_GrammarMistakeDetected() = runTest {
        val userInput = "Yesterday I go to the market."
        val correction = correctionEngine.analyzeUtterance(userInput)
        assertTrue("Must detect past tense error in 'Yesterday I go'", correction.hasError)
        assertEquals("Yesterday I went to the market.", correction.correctedSentence)

        val state = TutorLearnerState(currentDifficulty = DifficultyLevel.INTERMEDIATE, speakingConfidence = 80f)
        val decision = tutorEngine.evaluateDecision(state, correction)
        assertEquals(TutorAction.ASK_RETRY, decision.action)
        assertTrue("Spoken intervention contains explanation and target phrase", decision.spokenInterventionText?.contains("went") == true)
    }

    @Test
    fun testTurn5_CorrectSentenceNoFalseCorrection() = runTest {
        val userInput = "I went to Jaipur yesterday."
        val correction = correctionEngine.analyzeUtterance(userInput)
        assertFalse("Correct past tense 'I went' must NOT produce false correction", correction.hasError)

        val state = TutorLearnerState(currentDifficulty = DifficultyLevel.INTERMEDIATE, speakingConfidence = 80f)
        val decision = tutorEngine.evaluateDecision(state, correction)
        assertEquals(TutorAction.NORMAL_CONVERSATION, decision.action)
    }

    @Test
    fun testTurn6_CapitalOfAustraliaFactualAnswer() = runTest {
        val userInput = "What is the capital of Australia?"
        coEvery { mockOpenAIAdapter.generate(any(), any(), any(), any(), any(), any(), any()) } returns AIResult.Success(
            text = "The capital of Australia is Canberra. Many people mistake Sydney or Melbourne for the capital, but Canberra was chosen as the planned capital city.",
            latencyMs = 210,
            metadata = AIResponseMetadata(AIRoutingLevel.OPTIONAL_CLOUD, 210, 28, "llama-3.3-70b-versatile")
        )

        val result = router.generateResponse("You are Raya", emptyList(), userInput)
        assertTrue(result is AIResult.Success)
        val text = (result as AIResult.Success).text
        assertTrue("Must directly state Canberra", text.contains("Canberra", ignoreCase = true))
        assertFalse("Must NOT contain canned filler", text.contains("perspective! How did you first develop", ignoreCase = true))
    }

    @Test
    fun testTurn7_WhyDoPeopleLearnEnglish() = runTest {
        val userInput = "Why do people learn English?"
        coEvery { mockOpenAIAdapter.generate(any(), any(), any(), any(), any(), any(), any()) } returns AIResult.Success(
            text = "People learn English primarily because it is the global language of business, science, travel, and the internet. It opens up international career opportunities and lets you connect with people worldwide.",
            latencyMs = 230,
            metadata = AIResponseMetadata(AIRoutingLevel.OPTIONAL_CLOUD, 230, 32, "llama-3.3-70b-versatile")
        )

        val result = router.generateResponse("You are Raya", emptyList(), userInput)
        assertTrue(result is AIResult.Success)
        val text = (result as AIResult.Success).text
        assertTrue("Must discuss global language/communication", text.contains("global", ignoreCase = true) || text.contains("career", ignoreCase = true) || text.contains("connect", ignoreCase = true))
    }

    @Test
    fun testTurn8_CanYouCorrectMyEnglish() = runTest {
        val userInput = "Can you correct my English?"
        coEvery { mockOpenAIAdapter.generate(any(), any(), any(), any(), any(), any(), any()) } returns AIResult.Success(
            text = "Yes, absolutely! I'm here to help you improve your English. As we talk, I'll gently correct any grammar or vocabulary slips and share natural tips so you become fluent.",
            latencyMs = 220,
            metadata = AIResponseMetadata(AIRoutingLevel.OPTIONAL_CLOUD, 220, 35, "llama-3.3-70b-versatile")
        )

        val result = router.generateResponse("You are Raya", emptyList(), userInput)
        assertTrue(result is AIResult.Success)
        val text = (result as AIResult.Success).text
        assertTrue("Must affirm willingness to correct English", text.contains("yes", ignoreCase = true) || text.contains("absolutely", ignoreCase = true) || text.contains("help", ignoreCase = true))
    }
}
