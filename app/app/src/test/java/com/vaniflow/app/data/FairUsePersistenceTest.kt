package com.vaniflow.app.data

import com.vaniflow.app.data.local.db.entity.DailyUsageEntity
import com.vaniflow.app.domain.repository.DailyUsageRepository
import com.vaniflow.app.engine.ai.analytics.DailyConversationUsageTracker
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FairUsePersistenceTest {

    private lateinit var dailyUsageRepository: DailyUsageRepository

    @Before
    fun setup() {
        dailyUsageRepository = mockk(relaxed = true)
    }

    @Test
    fun `accumulated speaking time persists across tracker restart on same day`() = runTest {
        val testDate = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
        val savedEntity = DailyUsageEntity(
            date = testDate,
            speakingSeconds = 3600L, // 60 minutes
            inputTokens = 1200L,
            outputTokens = 2400L,
            totalRequests = 15,
            cacheHits = 4,
            savedTokens = 600L,
            updatedAt = System.currentTimeMillis()
        )

        coEvery { dailyUsageRepository.getUsageForDate(testDate) } returns savedEntity

        // Create fresh tracker instance simulating app restart
        val tracker = DailyConversationUsageTracker(dailyUsageRepository)
        tracker.loadPersistedUsage(testDate)

        assertEquals(60, tracker.getDailyMinutes())
        assertEquals(15, tracker.getTotalRequests())
        assertEquals(1200L, tracker.getInputTokens())
        assertEquals(2400L, tracker.getOutputTokens())
        assertFalse(tracker.isFairUseExceeded()) // 60 < 90

        // Add 35 minutes (2100 seconds) -> Total 95 minutes
        val slot = slot<DailyUsageEntity>()
        coEvery { dailyUsageRepository.saveUsage(capture(slot)) } returns Unit
        tracker.addSpeakingDurationSeconds(2100)

        assertEquals(95, tracker.getDailyMinutes())
        assertTrue(tracker.isFairUseExceeded()) // 95 >= 90 min fair-use target!
    }

    @Test
    fun `date rollover resets usage counters to zero on new day`() = runTest {
        val previousDay = "2020-01-01"
        val newDay = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())

        val previousEntity = DailyUsageEntity(
            date = previousDay,
            speakingSeconds = 5400L, // 90 min
            inputTokens = 3000L,
            outputTokens = 6000L,
            totalRequests = 30,
            cacheHits = 8,
            savedTokens = 1500L
        )

        coEvery { dailyUsageRepository.getUsageForDate(previousDay) } returns previousEntity
        coEvery { dailyUsageRepository.getUsageForDate(newDay) } returns null

        val tracker = DailyConversationUsageTracker(dailyUsageRepository)
        tracker.loadPersistedUsage(previousDay)
        assertEquals(90, tracker.getDailyMinutes())
        assertTrue(tracker.isFairUseExceeded())

        // Rollover to new day
        tracker.loadPersistedUsage(newDay)
        assertEquals(0, tracker.getDailyMinutes())
        assertEquals(0, tracker.getTotalRequests())
        assertFalse(tracker.isFairUseExceeded())
    }

    @Test
    fun `exact boundary check 89 min 59 sec is not exceeded but 90 min 00 sec is exceeded`() = runTest {
        val testDate = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
        val tracker = DailyConversationUsageTracker(dailyUsageRepository)
        tracker.loadPersistedUsage(testDate)

        // 89 minutes 59 seconds = 5399 seconds
        tracker.addSpeakingDurationSeconds(5399)
        assertEquals(89, tracker.getDailyMinutes())
        assertFalse("89:59 must NOT exceed fair-use limit", tracker.isFairUseExceeded())

        // Add 1 more second = 5400 seconds = exactly 90:00
        tracker.addSpeakingDurationSeconds(1)
        assertEquals(90, tracker.getDailyMinutes())
        assertTrue("90:00 MUST exceed fair-use limit", tracker.isFairUseExceeded())

        // Simulate app restart at 90:00
        val entityAt90 = DailyUsageEntity(
            date = testDate,
            speakingSeconds = 5400L,
            inputTokens = 2000L,
            outputTokens = 4000L,
            totalRequests = 25,
            cacheHits = 5,
            savedTokens = 500L
        )
        coEvery { dailyUsageRepository.getUsageForDate(testDate) } returns entityAt90

        val restartedTracker = DailyConversationUsageTracker(dailyUsageRepository)
        restartedTracker.loadPersistedUsage(testDate)
        assertEquals(90, restartedTracker.getDailyMinutes())
        assertTrue("Restart at 90:00 MUST STILL exceed fair-use limit", restartedTracker.isFairUseExceeded())
    }

    @Test
    fun `reaching 90 minutes enforces local or fallback routing and bypasses remote cloud AI`() = runTest {
        val testDate = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
        val entityAt90 = DailyUsageEntity(
            date = testDate,
            speakingSeconds = 5400L,
            inputTokens = 2000L,
            outputTokens = 4000L,
            totalRequests = 25,
            cacheHits = 5,
            savedTokens = 500L
        )
        coEvery { dailyUsageRepository.getUsageForDate(testDate) } returns entityAt90

        val tracker = DailyConversationUsageTracker(dailyUsageRepository)
        tracker.loadPersistedUsage(testDate)
        assertTrue(tracker.isFairUseExceeded())

        // Verify SmartResponseDecisionEngine decision
        val memoryManager = com.vaniflow.app.engine.ai.memory.ConversationMemoryManager()
        val cache = com.vaniflow.app.engine.ai.cache.DefaultAIResponseCache(mockk(relaxed = true))
        val providerRegistry = com.vaniflow.app.engine.ai.provider.ProviderRegistry(emptyList())

        val decisionEngine = com.vaniflow.app.engine.ai.routing.SmartResponseDecisionEngine(
            memoryManager = memoryManager,
            responseCache = cache,
            usageTracker = tracker,
            providerRegistry = providerRegistry
        )

        val decision = decisionEngine.evaluateDecision("Tell me about Jaipur", "raya", "general")
        assertTrue(
            "Fair use limit reached must force LOCAL_AI_REQUIRED or FALLBACK_REQUIRED",
            decision.type == com.vaniflow.app.engine.ai.routing.ResponseDecisionType.LOCAL_AI_REQUIRED ||
                decision.type == com.vaniflow.app.engine.ai.routing.ResponseDecisionType.FALLBACK_REQUIRED
        )
    }
}
