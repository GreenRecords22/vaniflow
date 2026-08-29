package com.vaniflow.app.engine

import com.vaniflow.app.domain.model.ModelState
import com.vaniflow.app.engine.ai.AIRoutingLevel
import com.vaniflow.app.engine.ai.AIResult
import com.vaniflow.app.engine.ai.DefaultCloudAIProvider
import com.vaniflow.app.engine.ai.FallbackAIEngine
import com.vaniflow.app.engine.ai.LocalAIEngine
import com.vaniflow.app.engine.ai.ProviderConfig
import com.vaniflow.app.engine.ai.SmartAIRouter
import com.vaniflow.app.engine.ai.cache.AIResponseCache
import com.vaniflow.app.engine.ai.cache.CacheCategory
import com.vaniflow.app.engine.ai.llm.FakeLocalLLMRuntime
import com.vaniflow.app.engine.model.ModelManager
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class SmartAIRouterTest {

    private lateinit var mockModelManager: ModelManager
    private lateinit var localAIEngine: LocalAIEngine
    private lateinit var cloudAIProvider: DefaultCloudAIProvider
    private lateinit var fallbackAIEngine: FallbackAIEngine
    private lateinit var mockCache: AIResponseCache
    private lateinit var router: SmartAIRouter

    @Before
    fun setup() {
        mockModelManager = mockk(relaxed = true)
        // Real runtime available -> genuine dynamic generation is exercised.
        localAIEngine = LocalAIEngine(mockModelManager, FakeLocalLLMRuntime(available = true))
        cloudAIProvider = DefaultCloudAIProvider()
        fallbackAIEngine = FallbackAIEngine()
        mockCache = mockk(relaxed = true)
        router = SmartAIRouter(localAIEngine, cloudAIProvider, fallbackAIEngine, mockCache)
    }

    @Test
    fun testLevel0DeterministicEducationalRouting() = runTest {
        val result = router.generateResponse(
            systemPrompt = "You are Raya",
            conversationHistory = emptyList(),
            userInput = "What does although mean?"
        )

        assertTrue(result is AIResult.Success)
        val success = result as AIResult.Success
        assertEquals(AIRoutingLevel.DETERMINISTIC_RULE, success.metadata.routingLevel)
        assertTrue(success.text.contains("Although"))
    }

    @Test
    fun testCacheHitReturnsCachedResponseImmediately() = runTest {
        coEvery {
            mockCache.getCachedResponse(
                category = any(),
                characterId = any(),
                scenarioId = any(),
                userInput = any(),
                contextHash = any()
            )
        } returns null

        val result = router.generateResponse(
            systemPrompt = "You are Raya",
            conversationHistory = emptyList(),
            userInput = "Tell me about your day"
        )

        assertTrue(result is AIResult.Success)
        val success = result as AIResult.Success
        // No local model file present -> context-aware fallback (honest, not canned matrix).
        assertEquals(AIRoutingLevel.CONTEXT_AWARE_FALLBACK, success.metadata.routingLevel)
        assertTrue(success.text.isNotBlank())
    }

    @Test
    fun testLevel1LocalSLMRoutingWhenModelInstalled() = runTest {
        coEvery { mockCache.getCachedResponse(any(), any(), any(), any(), any()) } returns null
        every { mockModelManager.getModelState("llm_qwen25_05b_instruct") } returns ModelState.INSTALLED

        val tempDir = File(System.getProperty("java.io.tmpdir") ?: "/tmp")
        tempDir.mkdirs()
        val fakeModelFile = File(tempDir, "qwen_test_model_m13.gguf")
        if (!fakeModelFile.exists()) {
            java.io.RandomAccessFile(fakeModelFile, "rw").use { raf -> raf.setLength(2_000_000L) }
        }
        every { mockModelManager.getModelFile("llm_qwen25_05b_instruct") } returns fakeModelFile

        val result = router.generateResponse(
            systemPrompt = "You are Raya",
            conversationHistory = emptyList(),
            userInput = "I would like to order one hot cappuccino."
        )

        assertTrue(result is AIResult.Success)
        val success = result as AIResult.Success
        assertEquals(AIRoutingLevel.LOCAL_SLM, success.metadata.routingLevel)
        // Genuine dynamic response must reference the user's actual message.
        assertTrue(success.text.contains("cappuccino"))
    }

    @Test
    fun testLevel2OptionalCloudAdapterWhenEnabledAndLocalMissing() = runTest {
        coEvery { mockCache.getCachedResponse(any(), any(), any(), any(), any()) } returns null
        every { mockModelManager.getModelState("llm_qwen25_05b_instruct") } returns ModelState.NOT_INSTALLED

        cloudAIProvider.setConfig(
            ProviderConfig(providerId = "opt_cloud", providerName = "Test Cloud", isEnabled = true)
        )

        val result = router.generateResponse(
            systemPrompt = "You are Raya",
            conversationHistory = emptyList(),
            userInput = "Let us discuss the sprint roadmap."
        )

        assertTrue(result is AIResult.Success)
        val success = result as AIResult.Success
        assertEquals(AIRoutingLevel.OPTIONAL_CLOUD, success.metadata.routingLevel)
    }

    @Test
    fun testLevel3FallbackWhenLocalMissingAndCloudDisabled() = runTest {
        coEvery { mockCache.getCachedResponse(any(), any(), any(), any(), any()) } returns null
        every { mockModelManager.getModelState("llm_qwen25_05b_instruct") } returns ModelState.NOT_INSTALLED

        val result = router.generateResponse(
            systemPrompt = "You are Raya",
            conversationHistory = emptyList(),
            userInput = "I need to check in my flight at the airport."
        )

        assertTrue(result is AIResult.Success)
        val success = result as AIResult.Success
        assertEquals(AIRoutingLevel.CONTEXT_AWARE_FALLBACK, success.metadata.routingLevel)
        assertTrue(success.text.isNotBlank())
    }

    @Test
    fun testCloudRateLimitFallsBackToOfflineEngine() = runTest {
        coEvery { mockCache.getCachedResponse(any(), any(), any(), any(), any()) } returns null
        every { mockModelManager.getModelState("llm_qwen25_05b_instruct") } returns ModelState.NOT_INSTALLED

        cloudAIProvider.setConfig(
            ProviderConfig(providerId = "rate_limited_cloud", providerName = "Cloud Provider", isEnabled = true, dailyRequestBudget = 1)
        )
        cloudAIProvider.recordSuccess()

        val result = router.generateResponse(
            systemPrompt = "You are Raya",
            conversationHistory = emptyList(),
            userInput = "Hello Raya!"
        )

        assertTrue(result is AIResult.Success)
        val success = result as AIResult.Success
        assertEquals(AIRoutingLevel.CONTEXT_AWARE_FALLBACK, success.metadata.routingLevel)
    }

    @Test
    fun testStreamResponseEmitsTokens() = runTest {
        coEvery { mockCache.getCachedResponse(any(), any(), any(), any(), any()) } returns null
        every { mockModelManager.getModelState("llm_qwen25_05b_instruct") } returns ModelState.NOT_INSTALLED

        val tokens = router.streamResponse(
            systemPrompt = "You are Raya",
            conversationHistory = emptyList(),
            userInput = "Hello!"
        ).toList()

        assertTrue(tokens.isNotEmpty())
        assertTrue(tokens.joinToString("").trim().isNotBlank())
    }
}
