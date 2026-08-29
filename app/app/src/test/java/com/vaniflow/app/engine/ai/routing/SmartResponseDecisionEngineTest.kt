package com.vaniflow.app.engine.ai.routing

import com.vaniflow.app.engine.ai.AIResult
import com.vaniflow.app.engine.ai.analytics.DailyConversationUsageTracker
import com.vaniflow.app.engine.ai.cache.AIResponseCache
import com.vaniflow.app.engine.ai.cache.CacheCategory
import com.vaniflow.app.engine.ai.cache.CachedAIResponse
import com.vaniflow.app.engine.ai.memory.ConversationMemoryManager
import com.vaniflow.app.engine.ai.provider.*
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * 20+ Comprehensive Regression Tests for Milestone 24:
 * Smart Token-Saving Decision Engine, Multi-Provider Failover, Quota Switching, and Memory Recall.
 */
class SmartResponseDecisionEngineTest {

    private lateinit var memoryManager: ConversationMemoryManager
    private lateinit var mockCache: AIResponseCache
    private lateinit var usageTracker: DailyConversationUsageTracker
    private lateinit var quotaManager: ProviderQuotaManager
    private lateinit var healthManager: ProviderHealthManager
    private lateinit var remotePrimary: RemoteAIProvider
    private lateinit var remoteSecondary: SecondaryRemoteAIProvider
    private lateinit var localProvider: LocalAIProvider
    private lateinit var fallbackProvider: FallbackAIProvider
    private lateinit var providerRegistry: ProviderRegistry
    private lateinit var decisionEngine: SmartResponseDecisionEngine

    @Before
    fun setup() {
        memoryManager = ConversationMemoryManager()
        mockCache = mockk(relaxed = true)
        usageTracker = DailyConversationUsageTracker()
        quotaManager = ProviderQuotaManager()
        healthManager = ProviderHealthManager(quotaManager)

        remotePrimary = RemoteAIProvider(healthManager)
        remoteSecondary = SecondaryRemoteAIProvider(healthManager)
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
    }

    @Test
    fun test1_ExactCacheHitAvoidsAPI() = runBlocking {
        val cached = CachedAIResponse(
            cacheKey = "key1",
            text = "The capital of India is New Delhi.",
            category = CacheCategory.KNOWLEDGE_CACHE,
            estimatedTokens = 12,
            isExpired = false
        )
        coEvery { mockCache.getCachedResponse(CacheCategory.KNOWLEDGE_CACHE, any(), any(), any(), any(), any()) } returns cached

        val decision = decisionEngine.evaluateDecision("what is the capital of india", "raya", "general")
        assertEquals(ResponseDecisionType.CACHE_EXACT, decision.type)
        assertTrue(decision.estimatedTokensAvoided > 0)
        assertFalse(decision.isSensitive)
    }

    @Test
    fun test2_EducationalDefinitionAvoidsAPI() = runBlocking {
        val decision = decisionEngine.evaluateDecision("define confident", "raya", "general")
        assertEquals(ResponseDecisionType.CACHE_EXACT, decision.type)
        assertTrue(decision.directResponse?.contains("confident", ignoreCase = true) == true)
        assertTrue(decision.estimatedTokensAvoided > 0)
    }

    @Test
    fun test3_FreshQuestionCallsRemoteAPI() = runBlocking {
        coEvery { mockCache.getCachedResponse(any(), any(), any(), any(), any(), any()) } returns null

        val decision = decisionEngine.evaluateDecision("What should I do this weekend in Jaipur?", "raya", "general")
        assertEquals(ResponseDecisionType.REMOTE_AI_REQUIRED, decision.type)
        assertEquals("remote_primary", decision.selectedProvider?.providerId)
    }

    @Test
    fun test4_PersonalQuestionAvoidsUnsafeKnowledgeCache() = runBlocking {
        val decision = decisionEngine.evaluateDecision("I feel very stressed about my exam tomorrow", "raya", "general")
        assertEquals(ResponseDecisionType.REMOTE_AI_REQUIRED, decision.type)
    }

    @Test
    fun test5_MemoryAnswerAvoidsUnnecessaryAPI() = runBlocking {
        memoryManager.addTurn("user", "I love hot masala dosas and filter coffee.")
        val decision = decisionEngine.evaluateDecision("What was the food I said I liked earlier?", "raya", "general")
        assertEquals(ResponseDecisionType.MEMORY_ONLY, decision.type)
        assertTrue(decision.directResponse?.contains("dosa", ignoreCase = true) == true)
        assertTrue(decision.estimatedTokensAvoided > 0)
    }

    @Test
    fun test6_NameMemoryRecall() = runBlocking {
        memoryManager.addTurn("user", "My name is Priya.")
        val decision = decisionEngine.evaluateDecision("What is my name?", "raya", "general")
        assertEquals(ResponseDecisionType.MEMORY_ONLY, decision.type)
        assertTrue(decision.directResponse?.contains("Priya") == true)
    }

    @Test
    fun test7_TravelMemoryRecall() = runBlocking {
        memoryManager.addTurn("user", "I visited Udaipur last winter.")
        val decision = decisionEngine.evaluateDecision("Where did I visit?", "raya", "general")
        assertEquals(ResponseDecisionType.MEMORY_ONLY, decision.type)
        assertTrue(decision.directResponse?.contains("Udaipur") == true)
    }

    @Test
    fun test8_ContextChangesProduceDifferentMemoryContext() {
        memoryManager.addTurn("user", "Hello Raya")
        memoryManager.addTurn("assistant", "Hi there!")
        memoryManager.addTurn("user", "I'm going to Goa next week.")

        val compact = memoryManager.getCompactContext()
        assertEquals("Travel & Exploration", compact.currentTopic)
        assertTrue(compact.userFacts.any { it.contains("Goa") })
    }

    @Test
    fun test9_CacheMissRoutesToRemoteAI() = runBlocking {
        coEvery { mockCache.getCachedResponse(any(), any(), any(), any(), any(), any()) } returns null
        val decision = decisionEngine.evaluateDecision("Tell me about quantum computing in simple terms", "raya", "general")
        assertEquals(ResponseDecisionType.REMOTE_AI_REQUIRED, decision.type)
    }

    @Test
    fun test10_PrimaryAPIFailureRoutesToSecondary() = runBlocking {
        // Disable Primary
        remotePrimary.config = remotePrimary.config.copy(isEnabled = false)
        val decision = decisionEngine.evaluateDecision("Tell me about astronomy", "raya", "general")
        assertEquals(ResponseDecisionType.REMOTE_AI_REQUIRED, decision.type)
        assertEquals("remote_secondary", decision.selectedProvider?.providerId)
    }

    @Test
    fun test11_SecondaryFailureRoutesToLocal() = runBlocking {
        remotePrimary.config = remotePrimary.config.copy(isEnabled = false)
        remoteSecondary.config = remoteSecondary.config.copy(isEnabled = false)

        val decision = decisionEngine.evaluateDecision("How is the weather?", "raya", "general")
        assertEquals(ResponseDecisionType.LOCAL_AI_REQUIRED, decision.type)
        assertEquals("local_vaniflow", decision.selectedProvider?.providerId)
    }

    @Test
    fun test12_LocalFailureRoutesToFallback() = runBlocking {
        remotePrimary.config = remotePrimary.config.copy(isEnabled = false)
        remoteSecondary.config = remoteSecondary.config.copy(isEnabled = false)
        every { localProvider.isAvailable() } returns false

        val decision = decisionEngine.evaluateDecision("Let's talk about books", "raya", "general")
        assertEquals(ResponseDecisionType.FALLBACK_REQUIRED, decision.type)
    }

    @Test
    fun test13_QuotaWarningAt80PercentSwitchesProvider() {
        val config = ProviderConfig(providerId = "test_p", providerName = "Test", rpd = 100)
        repeat(80) { quotaManager.recordUsage("test_p", 10) }
        assertTrue("80% usage must trigger quota warning", quotaManager.isQuotaWarning(config))
    }

    @Test
    fun test14_QuotaExhaustionDisablesProvider() {
        val config = ProviderConfig(providerId = "test_p", providerName = "Test", rpd = 100)
        repeat(100) { quotaManager.recordUsage("test_p", 10) }
        assertTrue("100% usage must exhaust quota", quotaManager.isQuotaExhausted(config))
    }

    @Test
    fun test15_RateLimit429TriggersFailover() {
        healthManager.recordFailure(remotePrimary, isRateLimit = true)
        assertFalse("429 must immediately mark provider unavailable", healthManager.isAvailable(remotePrimary))
    }

    @Test
    fun test16_TimeoutTriggersCircuitBreaker() {
        repeat(3) { healthManager.recordFailure(remotePrimary, isRateLimit = false) }
        assertFalse("3 consecutive timeouts must trip circuit breaker", healthManager.isAvailable(remotePrimary))
    }

    @Test
    fun test17_NinetyMinuteFairUsePolicyTransitionsToLocal() = runBlocking {
        usageTracker.addSpeakingDurationSeconds(90 * 60) // 90 mins
        assertTrue(usageTracker.isFairUseExceeded())

        val decision = decisionEngine.evaluateDecision("Let's continue chatting", "raya", "general")
        assertEquals(ResponseDecisionType.LOCAL_AI_REQUIRED, decision.type)
    }

    @Test
    fun test18_SensitiveCreditCardNotCached() = runBlocking {
        val decision = decisionEngine.evaluateDecision("My card number is 4111 2222 3333 4444", "raya", "general")
        assertTrue("Credit card must be flagged sensitive", decision.isSensitive)
    }

    @Test
    fun test19_SensitivePasswordNotCached() = runBlocking {
        val decision = decisionEngine.evaluateDecision("My secret password is pass123", "raya", "general")
        assertTrue("Password must be flagged sensitive", decision.isSensitive)
    }

    @Test
    fun test20_TokenSaverTracksAvoidedTokens() = runBlocking {
        memoryManager.addTurn("user", "My name is John.")
        val decision = decisionEngine.evaluateDecision("What is my name?", "raya", "general")
        assertTrue(decision.estimatedTokensAvoided > 0)
    }
}