package com.vaniflow.app.engine

import com.vaniflow.app.data.local.db.dao.AICacheDao
import com.vaniflow.app.data.local.db.entity.AICacheEntity
import com.vaniflow.app.engine.ai.AIRoutingLevel
import com.vaniflow.app.engine.ai.cache.CacheCategory
import com.vaniflow.app.engine.ai.cache.DefaultAIResponseCache
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AIResponseCacheTest {

    private lateinit var mockDao: AICacheDao
    private lateinit var cache: DefaultAIResponseCache

    @Before
    fun setup() {
        mockDao = mockk(relaxed = true)
        cache = DefaultAIResponseCache(mockDao)
    }

    @Test
    fun testCacheMissIncrementsMissCounter() = runTest {
        coEvery { mockDao.getByKey(any()) } returns null

        val result = cache.getCachedResponse(
            category = CacheCategory.KNOWLEDGE_CACHE,
            characterId = "raya",
            scenarioId = "coffee",
            userInput = "what does decaf mean?"
        )

        assertNull(result)
        assertEquals(1L, cache.usageStats.value.cacheMisses)
        assertEquals(0L, cache.usageStats.value.cacheHits)
    }

    @Test
    fun testCacheHitReturnsResponseAndTracksSavings() = runTest {
        val capturedEntity = AICacheEntity(
            cacheKey = "test_key",
            category = CacheCategory.KNOWLEDGE_CACHE.name,
            characterId = "raya",
            scenarioId = "coffee",
            promptVersion = "1.0",
            normalizedInput = "what does decaf mean?",
            responseText = "Decaf means decaffeinated coffee without caffeine.",
            tokensCount = 12,
            createdAt = System.currentTimeMillis(),
            lastAccessedAt = System.currentTimeMillis(),
            expiresAt = System.currentTimeMillis() + 100_000L
        )

        coEvery { mockDao.getByKey(any()) } returns capturedEntity

        val result = cache.getCachedResponse(
            category = CacheCategory.KNOWLEDGE_CACHE,
            characterId = "raya",
            scenarioId = "coffee",
            userInput = "what does decaf mean?"
        )

        assertNotNull(result)
        assertEquals("Decaf means decaffeinated coffee without caffeine.", result!!.text)
        assertEquals(1L, cache.usageStats.value.cacheHits)
        assertEquals(1L, cache.usageStats.value.cloudRequestsAvoided)
        assertEquals(12L, cache.usageStats.value.estimatedTokensSaved)
    }

    @Test
    fun testExpiredResponseTreatedAsMiss() = runTest {
        val expiredEntity = AICacheEntity(
            cacheKey = "expired_key",
            category = CacheCategory.CONVERSATION_CACHE.name,
            characterId = "raya",
            scenarioId = "coffee",
            promptVersion = "1.0",
            normalizedInput = "hello",
            responseText = "Hello there!",
            tokensCount = 3,
            createdAt = System.currentTimeMillis() - 500_000L,
            lastAccessedAt = System.currentTimeMillis() - 500_000L,
            expiresAt = System.currentTimeMillis() - 10_000L // Expired in the past
        )

        coEvery { mockDao.getByKey(any()) } returns expiredEntity

        val result = cache.getCachedResponse(
            category = CacheCategory.CONVERSATION_CACHE,
            characterId = "raya",
            scenarioId = "coffee",
            userInput = "hello"
        )

        assertNull("Expired entries must return null", result)
        assertEquals(1L, cache.usageStats.value.cacheMisses)
    }

    @Test
    fun testDoNotCacheCategoryBypassesCache() = runTest {
        val result = cache.getCachedResponse(
            category = CacheCategory.DO_NOT_CACHE,
            characterId = "raya",
            scenarioId = "coffee",
            userInput = "my personal life story"
        )

        assertNull(result)
        coVerify(exactly = 0) { mockDao.getByKey(any()) }
    }

    @Test
    fun testPutResponseEvictsWhenCapacityReached() = runTest {
        coEvery { mockDao.getEntryCount() } returns 500

        cache.putResponse(
            category = CacheCategory.KNOWLEDGE_CACHE,
            characterId = "raya",
            scenarioId = "coffee",
            userInput = "define espresso",
            responseText = "Espresso is concentrated coffee brewed under high pressure."
        )

        coVerify(atLeast = 1) { mockDao.evictOldest(any()) }
        coVerify(exactly = 1) { mockDao.insertOrUpdate(any()) }
    }
}
