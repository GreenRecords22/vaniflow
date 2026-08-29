package com.vaniflow.app.engine.ai.cache

import com.vaniflow.app.data.local.db.dao.AICacheDao
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Regression test for cache safety (Milestone 18, Phase 10 / criterion 7).
 *
 * A cache hit must NEVER cause two unrelated questions to receive the same
 * response. The cache key must differ when the normalized user input differs,
 * even when character/scenario/context/prompt-version are identical.
 */
class AIResponseCacheKeySafetyTest {

    @Test
    fun `different questions produce different cache keys`() = runTest {
        val dao = mockk<AICacheDao>(relaxed = true)
        val keySlot = slot<String>()
        coEvery { dao.getByKey(capture(keySlot)) } returns null

        val cache = DefaultAIResponseCache(dao)

        cache.getCachedResponse(
            category = CacheCategory.KNOWLEDGE_CACHE,
            characterId = "raya",
            scenarioId = "general",
            userInput = "what does confident mean",
            contextHash = "ctxA",
            promptVersion = "2.0"
        )
        val key1 = keySlot.captured

        cache.getCachedResponse(
            category = CacheCategory.KNOWLEDGE_CACHE,
            characterId = "raya",
            scenarioId = "general",
            userInput = "what does although mean",
            contextHash = "ctxA",
            promptVersion = "2.0"
        )
        val key2 = keySlot.captured

        assertNotEquals("Unrelated questions must not collide in the cache", key1, key2)
    }

    @Test
    fun `DO_NOT_CACHE category never returns a cached response`() = runTest {
        val dao = mockk<AICacheDao>(relaxed = true)
        val cache = DefaultAIResponseCache(dao)

        val result = cache.getCachedResponse(
            category = CacheCategory.DO_NOT_CACHE,
            characterId = "raya",
            scenarioId = "general",
            userInput = "how are you today",
            contextHash = "ctxB",
            promptVersion = "2.0"
        )
        // Conversational turns are never cached, so a null response forces a fresh generation.
        org.junit.Assert.assertNull(result)
    }
}
