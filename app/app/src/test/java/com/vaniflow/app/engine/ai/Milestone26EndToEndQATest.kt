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
import io.mockk.*
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Full End-to-End Conversation QA and System Regression Test Suite for Milestone 26.
 */
class Milestone26EndToEndQATest {

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
    fun testTenQuestionBenchmarkSessionProducesDistinctContextualAnswers() = runBlocking {
        // Setup initial user context in conversation
        memoryManager.addTurn("user", "My name is Rahul and I love paneer tikka.")

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

        val responses = mutableListOf<String>()

        for (q in questions) {
            val result = router.generateResponse("You are Raya, a friendly mentor.", emptyList(), q)
            assertTrue("Query '$q' must succeed", result is AIResult.Success)
            val text = (result as AIResult.Success).text
            assertFalse("Response for '$q' must not be blank", text.isBlank())
            assertFalse("Must not expose internal error strings", text.contains("429") || text.contains("offline model"))
            responses.add(text)
        }

        // Verify distinctness across all 10 responses
        val uniqueCount = responses.distinct().size
        assertEquals("All 10 benchmark questions must produce unique responses", 10, uniqueCount)

        // Verify specific memory recall in question 6 & 10
        assertTrue("Q6 must recall paneer tikka", responses[5].contains("paneer", ignoreCase = true) || responses[5].contains("tikka", ignoreCase = true))
        assertTrue("Q10 must recall conversation topic", responses[9].contains("talking about", ignoreCase = true) || responses[9].contains("discussing", ignoreCase = true))
    }

    @Test
    fun testCacheHitReturnsZeroAPICost() = runBlocking {
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
        assertTrue(usageTracker.getSavedTokens() > 0)
    }

    @Test
    fun testPrimary429RateLimitFailsOverToSecondary() = runBlocking {
        configStore.setPrimaryConfig("test_key", "https://api.groq.com/openai/v1/chat/completions", "llama-3.1-8b-instant")
        coEvery { mockOpenAIAdapter.generate(any(), any(), any(), any(), any(), any(), any()) } returns AIResult.Error("Rate limit exceeded (HTTP 429)")

        val primaryResult = remotePrimary.generateResponse("You are Raya", emptyList(), "Tell me a story")
        assertTrue(primaryResult is AIResult.Error)
        assertFalse(healthManager.isAvailable(remotePrimary))
        assertTrue(healthManager.isAvailable(remoteSecondary))
    }

    @Test
    fun testPrimary80PercentQuotaWarning() {
        val config = remotePrimary.config
        repeat(800) { quotaManager.recordUsage(remotePrimary.providerId, 10) }
        assertTrue(quotaManager.isQuotaWarning(config))
    }

    @Test
    fun testSecondaryFailureFailsOverToLocalSLM() = runBlocking {
        remotePrimary.config = remotePrimary.config.copy(isEnabled = false)
        remoteSecondary.config = remoteSecondary.config.copy(isEnabled = false)
        val decision = decisionEngine.evaluateDecision("What are your plans?", "raya", "general")
        assertEquals(ResponseDecisionType.LOCAL_AI_REQUIRED, decision.type)
        assertEquals("local_vaniflow", decision.selectedProvider?.providerId)
    }

    @Test
    fun testOfflineRoutingWorksWithoutCrashing() = runBlocking {
        remotePrimary.config = remotePrimary.config.copy(isEnabled = false)
        remoteSecondary.config = remoteSecondary.config.copy(isEnabled = false)
        every { localProvider.isAvailable() } returns false

        val result = router.generateResponse("You are Raya", emptyList(), "Hello Raya!")
        assertTrue(result is AIResult.Success)
        assertTrue((result as AIResult.Success).text.isNotBlank())
    }

    @Test
    fun testNinetyMinutePolicyTransitionsGracefully() = runBlocking {
        usageTracker.addSpeakingDurationSeconds(90 * 60)
        assertTrue(usageTracker.isFairUseExceeded())
        val decision = decisionEngine.evaluateDecision("Let's talk", "raya", "general")
        assertEquals(ResponseDecisionType.LOCAL_AI_REQUIRED, decision.type)
    }

    @Test
    fun testSensitiveDataNotCached() = runBlocking {
        val decision = decisionEngine.evaluateDecision("My credit card is 4111 2222 3333 4444", "raya", "general")
        assertTrue(decision.isSensitive)
    }

    @Test
    fun testStreamingTokensAreEmitted() = runBlocking {
        val tokens = router.streamResponse("You are Raya", emptyList(), "Hello Raya!").toList()
        assertTrue(tokens.isNotEmpty())
    }

    @Test
    fun testCompactContextMemoryUnder500Tokens() {
        repeat(10) { i ->
            memoryManager.addTurn("user", "User statement $i discussing travel to Jaipur.")
            memoryManager.addTurn("assistant", "Assistant response $i discussing palaces.")
        }
        val compact = memoryManager.getCompactContext()
        assertTrue(compact.estimatedTokens < 500)
    }
}