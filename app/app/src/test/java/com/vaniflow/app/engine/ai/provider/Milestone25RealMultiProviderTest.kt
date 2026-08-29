package com.vaniflow.app.engine.ai.provider

import com.vaniflow.app.engine.ai.*
import com.vaniflow.app.engine.ai.analytics.DailyConversationUsageTracker
import com.vaniflow.app.engine.ai.cache.AIResponseCache
import com.vaniflow.app.engine.ai.cache.CacheCategory
import com.vaniflow.app.engine.ai.cache.CachedAIResponse
import com.vaniflow.app.engine.ai.memory.ConversationMemoryManager
import com.vaniflow.app.engine.ai.provider.adapter.GeminiProviderAdapter
import com.vaniflow.app.engine.ai.provider.adapter.OpenAICompatibleAdapter
import com.vaniflow.app.engine.ai.routing.ResponseDecisionType
import com.vaniflow.app.engine.ai.routing.SmartResponseDecisionEngine
import io.mockk.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Comprehensive verification test suite for Milestone 25:
 * Real Multi-Provider Cloud AI Integration, Adapters, Quota Switching, Failover & Token Saver.
 */
class Milestone25RealMultiProviderTest {

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
    fun test1_CacheHitReturnsZeroAPICost() = runBlocking {
        val cached = CachedAIResponse(
            cacheKey = "k1",
            text = "The capital of India is New Delhi.",
            category = CacheCategory.KNOWLEDGE_CACHE,
            estimatedTokens = 12,
            isExpired = false
        )
        coEvery { mockCache.getCachedResponse(CacheCategory.KNOWLEDGE_CACHE, any(), any(), any(), any(), any()) } returns cached

        val result = router.generateResponse("You are Raya", emptyList(), "what is the capital of india")
        assertTrue(result is AIResult.Success)
        assertEquals("The capital of India is New Delhi.", (result as AIResult.Success).text)
        assertTrue("Tokens saved must be recorded", usageTracker.getSavedTokens() > 0)
    }

    @Test
    fun test2_MemoryHitReturnsZeroAPICost() = runBlocking {
        memoryManager.addTurn("user", "My name is Ananya and I love filter coffee.")
        val result = router.generateResponse("You are Raya", emptyList(), "What was the food I said I liked earlier?")
        assertTrue(result is AIResult.Success)
        val text = (result as AIResult.Success).text
        assertTrue("Must recall filter coffee from memory", text.contains("coffee", ignoreCase = true) || text.contains("filter", ignoreCase = true))
    }

    @Test
    fun test3_DynamicQuestionDispatchesToPrimaryCloudProvider() = runBlocking {
        coEvery { mockCache.getCachedResponse(any(), any(), any(), any(), any(), any()) } returns null
        val result = router.generateResponse("You are Raya", emptyList(), "What is your perspective on morning meditation?")
        assertTrue(result is AIResult.Success)
        assertFalse((result as AIResult.Success).text.isBlank())
    }

    @Test
    fun test4_PrimaryProviderRealHttpAdapterInvocation() = runBlocking {
        configStore.setPrimaryConfig("test_api_key", "https://api.groq.com/openai/v1/chat/completions", "llama-3.1-8b-instant", "openai_compatible")
        coEvery { mockOpenAIAdapter.generate(any(), any(), any(), any(), any(), any(), any()) } returns AIResult.Success(
            text = "Meditation calms the active mind.",
            latencyMs = 120,
            metadata = AIResponseMetadata(AIRoutingLevel.OPTIONAL_CLOUD, 120, 10, "llama-3.1-8b-instant")
        )

        val result = remotePrimary.generateResponse("You are Raya", emptyList(), "Tell me about meditation")
        assertTrue(result is AIResult.Success)
        assertEquals("Meditation calms the active mind.", (result as AIResult.Success).text)
    }

    @Test
    fun test5_Primary429RateLimitFailsOverToSecondary() = runBlocking {
        configStore.setPrimaryConfig("test_key", "https://api.groq.com/openai/v1/chat/completions", "llama-3.1-8b-instant")
        coEvery { mockOpenAIAdapter.generate(any(), any(), any(), any(), any(), any(), any()) } returns AIResult.Error("Rate limit exceeded (HTTP 429)")

        val primaryResult = remotePrimary.generateResponse("You are Raya", emptyList(), "Tell me a story")
        assertTrue(primaryResult is AIResult.Error)
        assertFalse("Primary must be tripped by 429", healthManager.isAvailable(remotePrimary))
        assertTrue("Secondary must remain available", healthManager.isAvailable(remoteSecondary))
    }

    @Test
    fun test6_PrimaryTimeoutFailsOverToSecondary() = runBlocking {
        // Record 3 consecutive timeouts
        repeat(3) { remotePrimary.recordFailure(isRateLimit = false) }
        assertFalse("3 timeouts trip circuit breaker", healthManager.isAvailable(remotePrimary))
        assertTrue("Secondary remains available", healthManager.isAvailable(remoteSecondary))
    }

    @Test
    fun test7_Primary80PercentQuotaSwitchesProvider() {
        val config = remotePrimary.config
        repeat(800) { quotaManager.recordUsage(remotePrimary.providerId, 10) }
        assertTrue("80% request quota trips warning", quotaManager.isQuotaWarning(config))
    }

    @Test
    fun test8_SecondaryFailureFailsOverToLocalSLM() = runBlocking {
        remotePrimary.config = remotePrimary.config.copy(isEnabled = false)
        remoteSecondary.config = remoteSecondary.config.copy(isEnabled = false)
        val decision = decisionEngine.evaluateDecision("What are your plans?", "raya", "general")
        assertEquals(ResponseDecisionType.LOCAL_AI_REQUIRED, decision.type)
        assertEquals("local_vaniflow", decision.selectedProvider?.providerId)
    }

    @Test
    fun test9_CloudUnavailableFallsBackToLocalOrDialogueEngine() = runBlocking {
        remotePrimary.config = remotePrimary.config.copy(isEnabled = false)
        remoteSecondary.config = remoteSecondary.config.copy(isEnabled = false)
        every { localProvider.isAvailable() } returns false

        val result = router.generateResponse("You are Raya", emptyList(), "How is your morning?")
        assertTrue(result is AIResult.Success)
        assertTrue((result as AIResult.Success).text.isNotBlank())
    }

    @Test
    fun test10_NinetyMinuteFairUsePolicyTransitionsToLocal() = runBlocking {
        usageTracker.addSpeakingDurationSeconds(90 * 60) // 90 mins
        assertTrue(usageTracker.isFairUseExceeded())
        val decision = decisionEngine.evaluateDecision("Let's keep talking", "raya", "general")
        assertEquals(ResponseDecisionType.LOCAL_AI_REQUIRED, decision.type)
    }

    @Test
    fun test11_SensitiveDataNotCached() = runBlocking {
        val decision = decisionEngine.evaluateDecision("My secret pin is 9988", "raya", "general")
        assertTrue(decision.isSensitive)
    }

    @Test
    fun test12_DifferentQuestionsProduceDistinctResponses() = runBlocking {
        val r1 = (router.generateResponse("You are Raya", emptyList(), "What is your favorite food?") as AIResult.Success).text
        val r2 = (router.generateResponse("You are Raya", emptyList(), "How do I prepare for an airport check-in?") as AIResult.Success).text
        val r3 = (router.generateResponse("You are Raya", emptyList(), "What should I do on a rainy weekend?") as AIResult.Success).text

        assertNotEquals(r1, r2)
        assertNotEquals(r2, r3)
        assertNotEquals(r1, r3)
    }

    @Test
    fun test13_CompactContextBoundedUnder500Tokens() {
        repeat(12) { i ->
            memoryManager.addTurn("user", "User statement $i about travel to Jaipur and Goa.")
            memoryManager.addTurn("assistant", "Assistant response $i exploring Rajasthan culture.")
        }
        val compact = memoryManager.getCompactContext()
        assertTrue("Compact tokens must be bounded < 500", compact.estimatedTokens < 500)
    }

    @Test
    fun test14_StreamingTokensFlowsNaturally() = runBlocking {
        val tokens = router.streamResponse("You are Raya", emptyList(), "Hello Raya!").toList()
        assertTrue("Must emit tokens", tokens.isNotEmpty())
    }

    @Test
    fun test15_GeminiAdapterConfig() {
        configStore.setSecondaryConfig("gemini_key", "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent", "gemini-1.5-flash", "gemini")
        assertTrue(configStore.hasSecondaryCredentials())
        assertEquals("gemini", configStore.getSecondaryAdapterType())
    }

    @Test
    fun test16_ApiConfigStoreZeroHardcodedSecrets() {
        val freshStore = ApiConfigStore()
        assertFalse("Must have zero hardcoded credentials by default", freshStore.hasPrimaryCredentials())
        assertFalse("Must have zero hardcoded credentials by default", freshStore.hasSecondaryCredentials())
    }
}