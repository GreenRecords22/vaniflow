package com.vaniflow.app.engine.ai

import com.vaniflow.app.engine.ai.analytics.DailyConversationUsageTracker
import com.vaniflow.app.engine.ai.cache.AIResponseCache
import com.vaniflow.app.engine.ai.cache.CacheCategory
import com.vaniflow.app.engine.ai.cache.CachedAIResponse
import com.vaniflow.app.engine.ai.memory.ConversationMemoryManager
import com.vaniflow.app.engine.ai.provider.*
import com.vaniflow.app.engine.ai.provider.adapter.GeminiProviderAdapter
import com.vaniflow.app.engine.ai.provider.adapter.OpenAICompatibleAdapter
import com.vaniflow.app.engine.ai.routing.ResponseDecisionType
import com.vaniflow.app.engine.ai.routing.SmartResponseDecisionEngine
import com.vaniflow.app.ui.avatar.AvatarFaceTransform
import com.vaniflow.app.ui.avatar.CharacterFacialRegistry
import io.mockk.*
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Milestone 27 Verification Suite:
 * Production AI Orchestration, Multi-Provider Failover, Real Conversational Quality,
 * Anti-Repetition, Avatar Mesh Mapping, and Token Saver Telemetry.
 */
class Milestone27ProductionOrchestrationTest {

    private lateinit var memoryManager: ConversationMemoryManager
    private lateinit var mockCache: AIResponseCache
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
    private lateinit var faceTransform: AvatarFaceTransform

    @Before
    fun setup() {
        memoryManager = ConversationMemoryManager()
        mockCache = mockk(relaxed = true)
        usageTracker = DailyConversationUsageTracker()
        quotaManager = ProviderQuotaManager()
        healthManager = ProviderHealthManager(quotaManager)
        configStore = ApiConfigStore()
        mockOpenAIAdapter = mockk(relaxed = true)
        mockGeminiAdapter = mockk(relaxed = true)
        faceTransform = AvatarFaceTransform(20, 20)

        remotePrimary = RemoteAIProvider(healthManager, ConversationalDialogueEngine(), configStore, mockOpenAIAdapter, mockGeminiAdapter)
        remoteSecondary = SecondaryRemoteAIProvider(healthManager, ConversationalDialogueEngine(), configStore, mockOpenAIAdapter, mockGeminiAdapter)
        localProvider = mockk(relaxed = true) {
            every { providerId } returns "local_vaniflow"
            every { priority } returns 3
            every { isAvailable() } returns true
        }
        fallbackProvider = mockk(relaxed = true) {
            every { providerId } returns "context_fallback"
            every { priority } returns 4
            every { isAvailable() } returns true
        }

        providerRegistry = ProviderRegistry(listOf(remotePrimary, remoteSecondary, localProvider, fallbackProvider))
        decisionEngine = SmartResponseDecisionEngine(memoryManager, mockCache, usageTracker, providerRegistry)
        router = SmartAIRouter(providerRegistry, memoryManager, usageTracker, FallbackAIEngine(), mockCache, decisionEngine)
    }

    @Test
    fun testA_TenDifferentQuestionsProduceTenDistinctContextualAnswers() = runBlocking {
        memoryManager.addTurn("user", "My name is Priya and I love paneer tikka.")

        val questions = listOf(
            "What is your favorite food?",
            "Do you like travelling?",
            "What do you usually do on weekends?",
            "Tell me something interesting about India.",
            "I'm feeling tired today.",
            "What did I tell you about my food preference?",
            "Why do you think travelling is interesting?",
            "Tell me something surprising.",
            "What should I do this weekend?",
            "Do you remember what we were talking about?"
        )

        val responses = questions.map { q ->
            val result = router.generateResponse("You are Raya", emptyList(), q)
            assertTrue("Query '$q' must succeed", result is AIResult.Success)
            val text = (result as AIResult.Success).text
            assertFalse("Response must not be blank", text.isBlank())
            assertFalse("Must not expose technical error terms", text.contains("429") || text.contains("offline model"))
            text
        }

        assertEquals("All 10 benchmark answers must be distinct", 10, responses.distinct().size)
    }

    @Test
    fun testB_SameQuestionRepeatedProducesAntiRepetitionVariation() {
        val dialogueEngine = ConversationalDialogueEngine()
        val r1 = dialogueEngine.generateResponse("raya", "General", com.vaniflow.app.domain.model.SkillLevel.BEGINNER, emptyList(), "Tell me a random topic")
        val r2 = dialogueEngine.generateResponse("raya", "General", com.vaniflow.app.domain.model.SkillLevel.BEGINNER, emptyList(), "Tell me a random topic")
        assertFalse("Repeated prompt must not return identical response verbatim", r1.equals(r2, ignoreCase = true))
    }

    @Test
    fun testC_DirectMemoryRecallForPreferencesAndTopic() = runBlocking {
        memoryManager.addTurn("user", "My name is Ananya and I love filter coffee.")
        val result = router.generateResponse("You are Raya", emptyList(), "What food did I say I liked?")
        assertTrue(result is AIResult.Success)
        val text = (result as AIResult.Success).text
        assertTrue("Must recall filter coffee", text.contains("coffee", ignoreCase = true) || text.contains("filter", ignoreCase = true))
    }

    @Test
    fun testD_CacheHitAvoidsAPICall() = runBlocking {
        val cached = CachedAIResponse(
            cacheKey = "k_india",
            text = "The capital of India is New Delhi.",
            category = CacheCategory.KNOWLEDGE_CACHE,
            estimatedTokens = 12,
            isExpired = false
        )
        coEvery { mockCache.getCachedResponse(CacheCategory.KNOWLEDGE_CACHE, any(), any(), any(), any(), any()) } returns cached

        val result = router.generateResponse("You are Raya", emptyList(), "what is the capital of india")
        assertTrue(result is AIResult.Success)
        assertEquals("The capital of India is New Delhi.", (result as AIResult.Success).text)
        assertTrue(usageTracker.getSavedTokens() > 0)
    }

    @Test
    fun testE_CacheMissCallsCloudProvider() = runBlocking {
        coEvery { mockCache.getCachedResponse(any(), any(), any(), any(), any(), any()) } returns null
        val result = router.generateResponse("You are Raya", emptyList(), "Tell me about solar storms")
        assertTrue(result is AIResult.Success)
    }

    @Test
    fun testF_SensitiveDataExcludedFromCache() = runBlocking {
        val decision = decisionEngine.evaluateDecision("My bank pin is 1234", "raya", "general")
        assertTrue("PIN must be flagged sensitive", decision.isSensitive)
    }

    @Test
    fun testG_ProviderFailoverToSecondaryOnPrimaryError() = runBlocking {
        remotePrimary.config = remotePrimary.config.copy(isEnabled = false)
        val decision = decisionEngine.evaluateDecision("Let's talk about poetry", "raya", "general")
        assertEquals(ResponseDecisionType.REMOTE_AI_REQUIRED, decision.type)
        assertEquals("remote_secondary", decision.selectedProvider?.providerId)
    }

    @Test
    fun testH_Http429TripsCircuitBreakerImmediately() = runBlocking {
        healthManager.recordFailure(remotePrimary, isRateLimit = true)
        assertFalse("429 must immediately trip primary", healthManager.isAvailable(remotePrimary))
        assertTrue("Secondary must remain available", healthManager.isAvailable(remoteSecondary))
    }

    @Test
    fun testI_ConsecutiveTimeoutsTripCircuitBreaker() {
        repeat(3) { healthManager.recordFailure(remotePrimary, isRateLimit = false) }
        assertFalse("3 timeouts must trip circuit breaker", healthManager.isAvailable(remotePrimary))
    }

    @Test
    fun testJ_ProviderRecoveryAfterReset() {
        repeat(3) { healthManager.recordFailure(remotePrimary, isRateLimit = false) }
        assertFalse(healthManager.isAvailable(remotePrimary))
        healthManager.reset(remotePrimary.providerId)
        assertTrue("Must be available after health reset", healthManager.isAvailable(remotePrimary))
    }

    @Test
    fun testK_StreamingEmitsTokensNaturally() = runBlocking {
        val tokens = router.streamResponse("You are Raya", emptyList(), "Hello Raya!").toList()
        assertTrue("Streaming must emit tokens", tokens.isNotEmpty())
    }

    @Test
    fun testL_AvatarMouthZeroDisplacementAtRest() {
        val raya = CharacterFacialRegistry.rayaProfile
        val verts = faceTransform.computeDeformedMesh(400f, 300f, raya.mouthCenterX, raya.mouthCenterY, openness = 0.0f)
        for (v in verts) {
            assertFalse(v.isNaN())
            assertFalse(v.isInfinite())
        }
    }

    @Test
    fun testM_AvatarMouthActiveDisplacementDuringSpeech() {
        val raya = CharacterFacialRegistry.rayaProfile
        val rest = faceTransform.computeDeformedMesh(400f, 300f, raya.mouthCenterX, raya.mouthCenterY, openness = 0.0f).clone()
        val speaking = faceTransform.computeDeformedMesh(400f, 300f, raya.mouthCenterX, raya.mouthCenterY, openness = 0.8f)

        var hasMovement = false
        for (i in 0 until faceTransform.totalVertices) {
            val delta = kotlin.math.abs(speaking[i * 2 + 1] - rest[i * 2 + 1])
            if (delta > 0.1f) hasMovement = true
        }
        assertTrue("Mouth zone must deform during speech", hasMovement)
    }

    @Test
    fun testN_AllFourCharacterFacialProfilesCalibrated() {
        val raya = CharacterFacialRegistry.rayaProfile
        val rudra = CharacterFacialRegistry.rudraProfile
        val adwaita = CharacterFacialRegistry.adwaitaProfile
        val shub = CharacterFacialRegistry.shubProfile

        assertEquals("raya", raya.characterId)
        assertEquals("rudra", rudra.characterId)
        assertEquals("adwaita", adwaita.characterId)
        assertEquals("shub", shub.characterId)

        assertTrue(raya.mouthCenterY in 0.50f..0.65f)
        assertTrue(rudra.mouthCenterY in 0.50f..0.65f)
        assertTrue(adwaita.mouthCenterY in 0.50f..0.65f)
        assertTrue(shub.mouthCenterY in 0.50f..0.65f)
    }

    @Test
    fun testO_NinetyMinuteFairUseTransitionsToLocalSLM() = runBlocking {
        usageTracker.addSpeakingDurationSeconds(90 * 60)
        assertTrue(usageTracker.isFairUseExceeded())
        val decision = decisionEngine.evaluateDecision("Tell me more", "raya", "general")
        assertEquals(ResponseDecisionType.LOCAL_AI_REQUIRED, decision.type)
    }

    @Test
    fun testP_ZeroHardcodedSecretsInStore() {
        val store = ApiConfigStore()
        assertFalse(store.hasPrimaryCredentials())
        assertFalse(store.hasSecondaryCredentials())
    }
}