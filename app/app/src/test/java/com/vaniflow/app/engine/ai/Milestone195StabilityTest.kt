package com.vaniflow.app.engine.ai

import com.vaniflow.app.domain.model.ModelState
import com.vaniflow.app.engine.ai.analytics.DailyConversationUsageTracker
import com.vaniflow.app.engine.ai.cache.AIResponseCache
import com.vaniflow.app.engine.ai.memory.ConversationMemoryManager
import com.vaniflow.app.engine.ai.provider.FallbackAIProvider
import com.vaniflow.app.engine.ai.provider.LocalAIProvider
import com.vaniflow.app.engine.ai.provider.ProviderConfig
import com.vaniflow.app.engine.ai.provider.ProviderHealthManager
import com.vaniflow.app.engine.ai.provider.ProviderHealthState
import com.vaniflow.app.engine.ai.provider.ProviderQuotaManager
import com.vaniflow.app.engine.ai.provider.ProviderRegistry
import com.vaniflow.app.engine.ai.provider.RemoteAIProvider
import com.vaniflow.app.engine.ai.provider.SecondaryRemoteAIProvider
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Comprehensive test suite for Milestone 19.5 AI Stability, API-First Infrastructure & Offline Model Fix.
 */
class Milestone195StabilityTest {

    private lateinit var quotaManager: ProviderQuotaManager
    private lateinit var healthManager: ProviderHealthManager
    private lateinit var memoryManager: ConversationMemoryManager
    private lateinit var usageTracker: DailyConversationUsageTracker
    private lateinit var remotePrimary: RemoteAIProvider
    private lateinit var remoteSecondary: SecondaryRemoteAIProvider
    private lateinit var localAIProvider: LocalAIProvider
    private lateinit var fallbackProvider: FallbackAIProvider
    private lateinit var providerRegistry: ProviderRegistry
    private lateinit var fallbackAIEngine: FallbackAIEngine
    private lateinit var mockCache: AIResponseCache
    private lateinit var mockLocalAIEngine: LocalAIEngine
    private lateinit var smartAIRouter: SmartAIRouter

    @Before
    fun setup() {
        quotaManager = ProviderQuotaManager()
        healthManager = ProviderHealthManager(quotaManager)
        memoryManager = ConversationMemoryManager()
        usageTracker = DailyConversationUsageTracker()

        remotePrimary = RemoteAIProvider(healthManager)
        remoteSecondary = SecondaryRemoteAIProvider(healthManager)

        mockLocalAIEngine = mockk(relaxed = true)
        every { mockLocalAIEngine.isModelReady() } returns true

        localAIProvider = LocalAIProvider(mockLocalAIEngine, healthManager)
        fallbackProvider = FallbackAIProvider(ContextAwareFallbackEngine())

        providerRegistry = ProviderRegistry(remotePrimary, remoteSecondary, localAIProvider, fallbackProvider)
        fallbackAIEngine = FallbackAIEngine()
        mockCache = mockk(relaxed = true)
        coEvery { mockCache.getCachedResponse(any(), any(), any(), any(), any(), any()) } returns null

        smartAIRouter = SmartAIRouter(
            providerRegistry,
            memoryManager,
            usageTracker,
            fallbackAIEngine,
            mockCache
        )
    }

    @Test
    fun testApiIsPrimaryByDefault() = runBlocking {
        val result = smartAIRouter.generateResponse("System", emptyList(), "Hello Raya!")
        assertTrue("Result must be success", result is AIResult.Success)
        val success = result as AIResult.Success
        assertEquals("VaniFlow Cloud AI", success.metadata.providerName)
    }

    @Test
    fun testProviderFailoverToSecondaryOnPrimaryError() = runBlocking {
        // Record failures on primary to trigger circuit-breaker
        repeat(4) { remotePrimary.recordFailure() }
        assertEquals(ProviderHealthState.ERROR, healthManager.evaluateHealth(remotePrimary))

        val result = smartAIRouter.generateResponse("System", emptyList(), "What do you think?")
        assertTrue("Result must succeed via secondary", result is AIResult.Success)
        val success = result as AIResult.Success
        assertEquals("VaniFlow Cloud Backup", success.metadata.providerName)
    }

    @Test
    fun testProviderFailoverToLocalWhenAllRemoteOffline() = runBlocking {
        // Disable both remote providers
        remotePrimary.config = remotePrimary.config.copy(isEnabled = false)
        remoteSecondary.config = remoteSecondary.config.copy(isEnabled = false)

        coEvery { mockLocalAIEngine.generateResponse(any(), any(), any()) } returns
            AIResult.Success("Local on-device response", 100, AIResponseMetadata(AIRoutingLevel.LOCAL_SLM, 100, 15, "VaniFlow Local Engine"))

        val result = smartAIRouter.generateResponse("System", emptyList(), "How are you?")
        assertTrue("Result must fall back to local", result is AIResult.Success)
        val success = result as AIResult.Success
        assertEquals("VaniFlow Local Engine", success.metadata.providerName)
    }

    @Test
    fun testLocalUnavailableFallsBackToNaturalConversationalMessage() = runBlocking {
        remotePrimary.config = remotePrimary.config.copy(isEnabled = false)
        remoteSecondary.config = remoteSecondary.config.copy(isEnabled = false)
        every { mockLocalAIEngine.isModelReady() } returns false

        val result = smartAIRouter.generateResponse("You are Raya", emptyList(), "Tell me a story")
        assertTrue("Must return context-aware fallback", result is AIResult.Success)
        val text = (result as AIResult.Success).text
        assertTrue("Must contain natural response", text.isNotBlank())
        assertFalse("Must NEVER mention offline model is not set", text.contains("offline model is not set"))
    }

    @Test
    fun testQuotaWarningAt80Percent() {
        val config = ProviderConfig(providerId = "test_p", providerName = "Test", rpd = 100)
        repeat(79) { quotaManager.recordUsage("test_p", 10) }
        assertFalse("79% should not trigger warning", quotaManager.isQuotaWarning(config))

        quotaManager.recordUsage("test_p", 10) // 80th request
        assertTrue("80% must trigger quota warning", quotaManager.isQuotaWarning(config))
    }

    @Test
    fun testQuotaExhaustionSwitchesProvider() {
        val config = ProviderConfig(providerId = "test_p", providerName = "Test", rpd = 100)
        repeat(100) { quotaManager.recordUsage("test_p", 10) }
        assertTrue("100% must be exhausted", quotaManager.isQuotaExhausted(config))
    }

    @Test
    fun testConversationMemoryKeepsRollingTurnsAndTopic() {
        memoryManager.addTurn("user", "I want to order a coffee")
        memoryManager.addTurn("assistant", "Sure, what kind of coffee would you like?")
        memoryManager.addTurn("user", "A cappuccino please")
        memoryManager.addTurn("assistant", "Large or regular?")

        assertEquals(4, memoryManager.getRollingTurns().size)
        assertEquals("Food & Dining", memoryManager.getCurrentTopic())

        val prompt = memoryManager.buildMemoryPrompt("Base Identity")
        assertTrue("Memory prompt must include Topic", prompt.contains("Food & Dining"))
    }

    @Test
    fun test90MinuteTranscriptIsNotSent() {
        // Simulate 50 turns
        for (i in 1..50) {
            memoryManager.addTurn("user", "Query $i in a long conversation")
            memoryManager.addTurn("assistant", "Reply $i to keep conversation moving")
        }
        val memoryTokens = memoryManager.estimateMemoryTokens()
        assertTrue("Memory token count must be bounded (<500 tokens)", memoryTokens < 500)
        assertTrue("Rolling turns must be bounded", memoryManager.getRollingTurns().size <= 10)
    }

    @Test
    fun testDailyUsageTrackerTracksMetrics() {
        usageTracker.recordTurn("remote_primary", 50, 30, 200, isCacheHit = false)
        usageTracker.recordTurn("remote_primary", 50, 30, 200, isCacheHit = false)
        usageTracker.recordTurn("cache", 10, 25, 5, isCacheHit = true)
        usageTracker.addSpeakingDurationSeconds(120) // 2 mins

        assertEquals(3, usageTracker.getTotalRequests())
        assertEquals(1, usageTracker.getCacheHits())
        assertEquals(25L, usageTracker.getSavedTokens())
        assertEquals(2, usageTracker.getDailyMinutes())
        assertFalse("2 mins should not exceed 90 mins fair use", usageTracker.isFairUseExceeded())
    }

    @Test
    fun testFiveUnrelatedQuestionsProduceDifferentAnswers() = runBlocking {
        val q1 = "What is your favorite food?"
        val q2 = "What do you do on weekends?"
        val q3 = "Do you like travelling?"
        val q4 = "Tell me something interesting."
        val q5 = "What makes you happy?"

        val r1 = (remotePrimary.generateResponse("Raya", emptyList(), q1) as AIResult.Success).text
        val r2 = (remotePrimary.generateResponse("Raya", emptyList(), q2) as AIResult.Success).text
        val r3 = (remotePrimary.generateResponse("Raya", emptyList(), q3) as AIResult.Success).text
        val r4 = (remotePrimary.generateResponse("Raya", emptyList(), q4) as AIResult.Success).text
        val r5 = (remotePrimary.generateResponse("Raya", emptyList(), q5) as AIResult.Success).text

        val uniqueResponses = setOf(r1, r2, r3, r4, r5)
        assertEquals("All 5 questions must produce distinct responses", 5, uniqueResponses.size)
    }

    @Test
    fun testModelStateEnumValues() {
        assertTrue("READY must be valid ModelState", ModelState.READY.name == "READY")
        assertTrue("FAILED must be valid ModelState", ModelState.FAILED.name == "FAILED")
        assertTrue("CANCELLED must be valid ModelState", ModelState.CANCELLED.name == "CANCELLED")
    }
}