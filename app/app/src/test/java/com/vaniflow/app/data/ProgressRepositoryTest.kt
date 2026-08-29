package com.vaniflow.app.data

import com.vaniflow.app.data.local.db.dao.SessionDao
import com.vaniflow.app.data.local.db.entity.SessionEntity
import com.vaniflow.app.data.repository.DefaultProgressRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class ProgressRepositoryTest {

    private lateinit var mockSessionDao: SessionDao
    private lateinit var repository: DefaultProgressRepository

    @Before
    fun setup() {
        mockSessionDao = mockk(relaxed = true)
        repository = DefaultProgressRepository(mockSessionDao)
    }

    @Test
    fun testEmptyDatabaseReturnsZeroMetrics() = runTest {
        every { mockSessionDao.getAllSessions() } returns flowOf(emptyList())

        val progress = repository.getProgress().first()

        assertEquals(0, progress.totalMinutes)
        assertEquals(0, progress.sessionCount)
        assertEquals(0, progress.currentStreak)
        assertEquals(0, progress.longestStreak)
        assertEquals(0, progress.averageFluency)
    }

    @Test
    fun testAggregationAcrossMultipleSessions() = runTest {
        val now = System.currentTimeMillis()
        val sessions = listOf(
            SessionEntity(
                id = "s1",
                characterId = "raya",
                scenarioId = "coffee",
                startTimeMs = now,
                endTimeMs = now + 300_000L,
                speakingTimeMinutes = 5,
                fluencyScore = 80,
                grammarScore = 75,
                pronunciationScore = 90,
                vocabularyScore = 70,
                strongestArea = "Pronunciation",
                focusNext = "Past Tense"
            ),
            SessionEntity(
                id = "s2",
                characterId = "rudra",
                scenarioId = "interview",
                startTimeMs = now - 86400_000L, // Yesterday
                endTimeMs = now - 86400_000L + 420_000L,
                speakingTimeMinutes = 7,
                fluencyScore = 90,
                grammarScore = 85,
                pronunciationScore = 92,
                vocabularyScore = 80,
                strongestArea = "Fluency",
                focusNext = "Prepositions"
            )
        )

        every { mockSessionDao.getAllSessions() } returns flowOf(sessions)

        val progress = repository.getProgress().first()

        assertEquals(12, progress.totalMinutes) // 5 + 7
        assertEquals(2, progress.sessionCount)
        assertEquals(85, progress.averageFluency) // (80 + 90) / 2
        assertEquals(80, progress.averageGrammar) // (75 + 85) / 2
        assertEquals(91, progress.averagePronunciation) // (90 + 92) / 2
        assertEquals(75, progress.averageVocabulary) // (70 + 80) / 2
        assertEquals(2, progress.currentStreak)
        assertEquals(2, progress.longestStreak)
    }

    @Test
    fun testStreakCalculationTodayAndYesterday() {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val todayMs = today.atStartOfDay(zone).toInstant().toEpochMilli() + 3600_000L
        val yesterdayMs = today.minusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() + 3600_000L
        val twoDaysAgoMs = today.minusDays(2).atStartOfDay(zone).toInstant().toEpochMilli() + 3600_000L

        val timestamps = listOf(todayMs, yesterdayMs, twoDaysAgoMs)
        val (current, longest) = repository.calculateStreak(timestamps, todayMs)

        assertEquals(3, current)
        assertEquals(3, longest)
    }

    @Test
    fun testStreakCalculationYesterdayOnlyPreservesStreak() {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val todayMs = today.atStartOfDay(zone).toInstant().toEpochMilli() + 3600_000L
        val yesterdayMs = today.minusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() + 3600_000L

        val timestamps = listOf(yesterdayMs)
        val (current, longest) = repository.calculateStreak(timestamps, todayMs)

        assertEquals(1, current)
        assertEquals(1, longest)
    }

    @Test
    fun testStreakCalculationMissingDayResetsCurrentStreak() {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val todayMs = today.atStartOfDay(zone).toInstant().toEpochMilli() + 3600_000L
        val threeDaysAgoMs = today.minusDays(3).atStartOfDay(zone).toInstant().toEpochMilli() + 3600_000L
        val fourDaysAgoMs = today.minusDays(4).atStartOfDay(zone).toInstant().toEpochMilli() + 3600_000L

        val timestamps = listOf(threeDaysAgoMs, fourDaysAgoMs)
        val (current, longest) = repository.calculateStreak(timestamps, todayMs)

        assertEquals(0, current)
        assertEquals(2, longest)
    }

    @Test
    fun testMultipleSessionsSameDayCountAsOneDayStreak() {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val todayMs1 = today.atStartOfDay(zone).toInstant().toEpochMilli() + 1000_000L
        val todayMs2 = today.atStartOfDay(zone).toInstant().toEpochMilli() + 5000_000L

        val timestamps = listOf(todayMs1, todayMs2)
        val (current, longest) = repository.calculateStreak(timestamps, todayMs1)

        assertEquals(1, current)
        assertEquals(1, longest)
    }
}
