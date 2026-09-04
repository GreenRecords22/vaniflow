package com.vaniflow.app.engine.ai

import com.vaniflow.app.engine.ai.analytics.DailyConversationUsageTracker
import com.vaniflow.app.engine.ai.cache.AIResponseCache
import com.vaniflow.app.engine.ai.cache.AIUsageStats
import com.vaniflow.app.engine.ai.cache.CacheCategory
import com.vaniflow.app.engine.ai.cache.CachedAIResponse
import com.vaniflow.app.engine.ai.memory.ConversationMemoryManager
import com.vaniflow.app.engine.ai.provider.AIProvider
import com.vaniflow.app.engine.ai.provider.ProviderConfig
import com.vaniflow.app.engine.ai.provider.ProviderHealthState
import com.vaniflow.app.engine.ai.provider.ProviderRegistry
import com.vaniflow.app.engine.ai.routing.SmartResponseDecisionEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * P7.5 Provider Path Execution Test.
 * Verifies Cloud Provider -> Local Qwen -> Emergency Fallback cascade behavior and Level 0 Cache.
 */
class P7_5_ProviderPathTest {

    private lateinit var memoryManager: ConversationMemoryManager
    private lateinit var usageTracker: DailyConversationUsageTracker
    private lateinit var fallbackAIEngine: FallbackAIEngine

    private class FakeTestProvider(
        override val providerId: String,
        override val providerName: String,
        override val priority: Int,
        var shouldSucceed: Boolean = true,
        var cannedResponse: String = "Test response"
    ) : AIProvider {
        override val config: ProviderConfig = ProviderConfig(providerId, providerName, isEnabled = true, priority = priority)
        override fun isAvailable(): Boolean = shouldSucceed
        override fun getHealthState(): ProviderHealthState = if (shouldSucceed) ProviderHealthState.AVAILABLE else ProviderHealthState.ERROR
        override suspend fun generateResponse(systemPrompt: String, conversationHistory: List<AITurn>, userInput: String): AIResult =
            if (shouldSucceed) AIResult.Success(cannedResponse, 50, AIResponseMetadata(AIRoutingLevel.OPTIONAL_CLOUD, 50, 10, providerName))
            else AIResult.Error("Simulated network outage")
        override fun streamResponse(systemPrompt: String, conversationHistory: List<AITurn>, userInput: String): Flow<String> = flow {
            if (shouldSucceed) {
                for (word in cannedResponse.split(" ")) emit(" ")
            }
        }
        override fun recordSuccess(latencyMs: Long, tokensGenerated: Int) {}
        override fun recordFailure(isRateLimit: Boolean) {}
    }

    private object InMemoryTestCache : AIResponseCache {
        val map = mutableMapOf<String, String>()
        override val usageStats: StateFlow<AIUsageStats> = MutableStateFlow(AIUsageStats())
        override suspend fun getCachedResponse(category: CacheCategory, characterId: String, scenarioId: String, userInput: String, contextHash: String, promptVersion: String): CachedAIResponse? {
            if (category == CacheCategory.DO_NOT_CACHE) return null
            val text = map[userInput.lowercase().trim()] ?: return null
            return CachedAIResponse("key", text, category, 10, false)
        }
        override suspend fun putResponse(category: CacheCategory, characterId: String, scenarioId: String, userInput: String, responseText: String, contextHash: String, promptVersion: String) {
            if (category != CacheCategory.DO_NOT_CACHE) {
                map[userInput.lowercase().trim()] = responseText
            }
        }
        override suspend fun recordGeneration(routingLevel: AIRoutingLevel, tokens: Int) {}
        override suspend fun clearCache(): Int = 0
        override suspend fun purgeExpired(): Int = 0
    }

    @Before
    fun setUp() {
        memoryManager = ConversationMemoryManager()
        usageTracker = DailyConversationUsageTracker()
        fallbackAIEngine = FallbackAIEngine()
        RepetitionGuard.reset()
    }

    @Test
    fun test01_cloudProviderPrimaryExecution() = runBlocking {
        val cloudProvider = FakeTestProvider("remote_groq", "Groq Llama-3.3-70B", 1, shouldSucceed = true, cannedResponse = "I recommend visiting the royal fort in Jaipur!")
        val localProvider = FakeTestProvider("local_qwen", "Qwen2.5-0.5B", 3, shouldSucceed = true, cannedResponse = "Local response")
        val registry = ProviderRegistry(listOf(cloudProvider, localProvider))
        val decisionEngine = SmartResponseDecisionEngine(memoryManager, InMemoryTestCache, usageTracker, registry)

        val router = SmartAIRouter(
            providerRegistry = registry,
            memoryManager = memoryManager,
            usageTracker = usageTracker,
            fallbackAIEngine = fallbackAIEngine,
            aiResponseCache = InMemoryTestCache,
            decisionEngine = decisionEngine
        )

        val res = router.generateResponse("SYSTEM: you are Raya.", emptyList(), "What should I see in Jaipur?")
        assertTrue(res is AIResult.Success)
        val success = res as AIResult.Success
        assertEquals("Groq Llama-3.3-70B", success.metadata.providerName)
        assertEquals("I recommend visiting the royal fort in Jaipur!", success.text)
    }

    @Test
    fun test02_cloudFailureCascadesToLocalQwen() = runBlocking {
        val failingCloud = FakeTestProvider("remote_groq", "Groq Llama-3.3-70B", 1, shouldSucceed = false)
        val localQwen = FakeTestProvider("local_qwen", "Qwen2.5-0.5B On-Device", 3, shouldSucceed = true, cannedResponse = "Local on-device answers your question directly.")
        val registry = ProviderRegistry(listOf(failingCloud, localQwen))
        val decisionEngine = SmartResponseDecisionEngine(memoryManager, InMemoryTestCache, usageTracker, registry)

        val router = SmartAIRouter(
            providerRegistry = registry,
            memoryManager = memoryManager,
            usageTracker = usageTracker,
            fallbackAIEngine = fallbackAIEngine,
            aiResponseCache = InMemoryTestCache,
            decisionEngine = decisionEngine
        )

        val res = router.generateResponse("SYSTEM: you are Raya.", emptyList(), "How do I make pasta?")
        assertTrue(res is AIResult.Success)
        val success = res as AIResult.Success
        assertEquals("Qwen2.5-0.5B On-Device", success.metadata.providerName)
    }

    @Test
    fun test03_allProvidersFailureActivatesEmergencyFallback() = runBlocking {
        val failingCloud = FakeTestProvider("remote_groq", "Groq Llama-3.3-70B", 1, shouldSucceed = false)
        val failingLocal = FakeTestProvider("local_qwen", "Qwen2.5-0.5B", 3, shouldSucceed = false)
        val registry = ProviderRegistry(listOf(failingCloud, failingLocal))
        val decisionEngine = SmartResponseDecisionEngine(memoryManager, InMemoryTestCache, usageTracker, registry)

        val router = SmartAIRouter(
            providerRegistry = registry,
            memoryManager = memoryManager,
            usageTracker = usageTracker,
            fallbackAIEngine = fallbackAIEngine,
            aiResponseCache = InMemoryTestCache,
            decisionEngine = decisionEngine
        )

        val res = router.generateResponse("SYSTEM: you are Raya.", emptyList(), "Tell me a story.")
        assertTrue(res is AIResult.Success)
        val success = res as AIResult.Success
        assertEquals(AIRoutingLevel.EMERGENCY_FALLBACK, success.metadata.routingLevel)
        assertEquals("VaniFlow Emergency Fallback", success.metadata.providerName)
    }

    @Test
    fun test04_level0KnowledgeCacheExactHit() = runBlocking {
        InMemoryTestCache.putResponse(
            category = CacheCategory.KNOWLEDGE_CACHE,
            characterId = "raya",
            scenarioId = "general",
            userInput = "how many days in a leap year",
            responseText = "There are 366 days in a leap year.",
            contextHash = "global_knowledge",
            promptVersion = "1.0"
        )

        val cloudProvider = FakeTestProvider("remote_groq", "Groq Llama-3.3-70B", 1, shouldSucceed = true, cannedResponse = "Unused remote response")
        val registry = ProviderRegistry(listOf(cloudProvider))
        val decisionEngine = SmartResponseDecisionEngine(memoryManager, InMemoryTestCache, usageTracker, registry)

        val router = SmartAIRouter(
            providerRegistry = registry,
            memoryManager = memoryManager,
            usageTracker = usageTracker,
            fallbackAIEngine = fallbackAIEngine,
            aiResponseCache = InMemoryTestCache,
            decisionEngine = decisionEngine
        )

        val res = router.generateResponse("SYSTEM", emptyList(), "how many days in a leap year")
        assertTrue(res is AIResult.Success)
        val success = res as AIResult.Success
        assertEquals(AIRoutingLevel.DETERMINISTIC_RULE, success.metadata.routingLevel)
        assertEquals("Local Knowledge Cache", success.metadata.providerName)
        assertEquals("There are 366 days in a leap year.", success.text)
    }
}
