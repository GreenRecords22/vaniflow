package com.vaniflow.app.engine.ai

import com.vaniflow.app.engine.ai.cache.AIResponseCache
import com.vaniflow.app.engine.ai.cache.AIUsageStats
import com.vaniflow.app.engine.model.ModelManager
import io.mockk.mockk
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression test for Milestone 18, Phase 1 / Phase 6.
 *
 * Before M18 the local AI path in [SmartAIRouter.streamResponse] called
 * `collectJoined()` on the underlying stream, buffering the ENTIRE response
 * before emitting a single token — which defeated sentence-by-sentence TTS and
 * made the UI feel frozen. This test proves tokens are now forwarded live.
 */
class SmartAIRouterStreamingTest {

    /** Emits two tokens with a clear gap so we can prove they are NOT buffered. */
    private class FakeStreamingLocalAIEngine : LocalAIEngine(mockk<ModelManager>(relaxed = true)) {
        override fun isModelReady(): Boolean = true
        override fun streamResponse(
            systemPrompt: String,
            conversationHistory: List<AITurn>,
            userInput: String
        ): Flow<String> = flow {
            emit("Hello ")
            delay(250)
            emit("world")
        }
    }

    private object NoOpCache : AIResponseCache {
        override val usageStats: StateFlow<AIUsageStats> = MutableStateFlow(AIUsageStats())
        override suspend fun getCachedResponse(
            category: com.vaniflow.app.engine.ai.cache.CacheCategory,
            characterId: String,
            scenarioId: String,
            userInput: String,
            contextHash: String,
            promptVersion: String
        ) = null
        override suspend fun putResponse(
            category: com.vaniflow.app.engine.ai.cache.CacheCategory,
            characterId: String,
            scenarioId: String,
            userInput: String,
            responseText: String,
            contextHash: String,
            promptVersion: String
        ) = Unit
        override suspend fun recordGeneration(routingLevel: AIRoutingLevel, tokens: Int) = Unit
        override suspend fun clearCache(): Int = 0
        override suspend fun purgeExpired(): Int = 0
    }

    @Test
    fun streamResponse_forwardsTokensLiveWithoutBuffering() = runBlocking {
        val router = SmartAIRouter(
            localAIEngine = FakeStreamingLocalAIEngine(),
            cloudAIProvider = DefaultCloudAIProvider(),
            fallbackAIEngine = FallbackAIEngine(),
            aiResponseCache = NoOpCache
        )

        val events = mutableListOf<Pair<Long, String>>()
        val t0 = System.currentTimeMillis()

        val job = launch {
            router.streamResponse("SYSTEM: you are Raya.", emptyList(), "How are you?")
                .collect { events.add((System.currentTimeMillis() - t0) to it) }
        }

        // Wait until both tokens have been forwarded (cancels the in-flight flow after).
        withTimeoutOrNull(2000) {
            while (events.size < 2) delay(10)
        }
        job.cancel()

        assertTrue("both streamed tokens were forwarded", events.size >= 2)
        // The fake emits "Hello " then waits 250ms before "world". With live streaming
        // the gap between forwarded tokens matches that real delay; a buffered router
        // would have collected the whole response first and emitted both tokens back-to-back.
        val gapMs = events[1].first - events[0].first
        assertTrue(
            "inter-token gap ($gapMs ms) proves live forwarding, not full-response buffering",
            gapMs > 150
        )
    }
}
