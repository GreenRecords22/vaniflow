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
        val testDate = "2026-08-29"
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
        val previousDay = "2026-08-28"
        val newDay = "2026-08-29"

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
}
