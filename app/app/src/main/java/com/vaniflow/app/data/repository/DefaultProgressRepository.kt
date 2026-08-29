package com.vaniflow.app.data.repository

import com.vaniflow.app.data.local.db.dao.SessionDao
import com.vaniflow.app.data.local.db.entity.SessionEntity
import com.vaniflow.app.domain.repository.ImprovementStat
import com.vaniflow.app.domain.repository.ProgressData
import com.vaniflow.app.domain.repository.ProgressRepository
import com.vaniflow.app.domain.repository.WeeklyDayData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultProgressRepository @Inject constructor(
    private val sessionDao: SessionDao
) : ProgressRepository {

    override fun getProgress(): Flow<ProgressData> {
        return sessionDao.getAllSessions().map { sessions ->
            computeProgressData(sessions)
        }
    }

    override fun calculateStreak(
        sessionTimestamps: List<Long>,
        nowEpochMs: Long
    ): Pair<Int, Int> {
        if (sessionTimestamps.isEmpty()) return Pair(0, 0)

        val zoneId = ZoneId.systemDefault()
        val today = Instant.ofEpochMilli(nowEpochMs).atZone(zoneId).toLocalDate()
        val yesterday = today.minusDays(1)

        val practiceDates = sessionTimestamps
            .map { Instant.ofEpochMilli(it).atZone(zoneId).toLocalDate() }
            .distinct()
            .sortedDescending()

        // 1. Current Streak
        var currentStreak = 0
        val startDate = when {
            practiceDates.contains(today) -> today
            practiceDates.contains(yesterday) -> yesterday
            else -> null
        }

        if (startDate != null) {
            var checkDate: LocalDate = startDate
            while (practiceDates.contains(checkDate)) {
                currentStreak++
                checkDate = checkDate.minusDays(1)
            }
        }

        // 2. Longest Streak
        val ascendingDates = practiceDates.sorted()
        var longestStreak = 0
        var tempStreak = 0
        var prevDate: LocalDate? = null

        for (date in ascendingDates) {
            if (prevDate == null || date == prevDate.plusDays(1)) {
                tempStreak++
            } else {
                tempStreak = 1
            }
            if (tempStreak > longestStreak) {
                longestStreak = tempStreak
            }
            prevDate = date
        }

        return Pair(currentStreak, maxOf(longestStreak, currentStreak))
    }

    private fun computeProgressData(sessions: List<SessionEntity>): ProgressData {
        if (sessions.isEmpty()) {
            return ProgressData(
                totalMinutes = 0,
                sessionCount = 0,
                currentStreak = 0,
                longestStreak = 0,
                weeklyData = generateEmptyWeek(),
                averageFluency = 0,
                averageGrammar = 0,
                averagePronunciation = 0,
                averageVocabulary = 0,
                improvements = emptyList(),
                aiCoachRecommendation = "Start your first conversation today to unlock speaking insights!"
            )
        }

        val totalMinutes = sessions.sumOf { it.speakingTimeMinutes }
        val sessionCount = sessions.size
        val timestamps = sessions.map { it.startTimeMs }
        val (currentStreak, longestStreak) = calculateStreak(timestamps)

        // Averages
        val avgFluency = (sessions.sumOf { it.fluencyScore } / sessionCount).coerceIn(0, 100)
        val avgGrammar = (sessions.sumOf { it.grammarScore } / sessionCount).coerceIn(0, 100)
        val avgPronunciation = (sessions.sumOf { it.pronunciationScore } / sessionCount).coerceIn(0, 100)
        val avgVocabulary = (sessions.sumOf { it.vocabularyScore } / sessionCount).coerceIn(0, 100)

        // Weekly breakdown (Current calendar week Monday to Sunday)
        val zoneId = ZoneId.systemDefault()
        val today = LocalDate.now(zoneId)
        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

        val daysOfWeek = (0..6).map { monday.plusDays(it.toLong()) }
        val maxDayMinutes = daysOfWeek.maxOfOrNull { date ->
            sessions.filter {
                Instant.ofEpochMilli(it.startTimeMs).atZone(zoneId).toLocalDate() == date
            }.sumOf { it.speakingTimeMinutes }
        }?.coerceAtLeast(1) ?: 1

        val weeklyData = daysOfWeek.map { date ->
            val daySessions = sessions.filter {
                Instant.ofEpochMilli(it.startTimeMs).atZone(zoneId).toLocalDate() == date
            }
            val dayMinutes = daySessions.sumOf { it.speakingTimeMinutes }
            val fraction = (dayMinutes.toFloat() / maxDayMinutes.toFloat()).coerceIn(0.15f, 1.0f)
            val dayLabel = date.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercaseChar() }

            WeeklyDayData(
                dayLabel = dayLabel,
                minutes = dayMinutes,
                sessionCount = daySessions.size,
                fraction = if (dayMinutes > 0) fraction else 0.15f,
                isToday = (date == today)
            )
        }

        val improvements = listOf(
            ImprovementStat(
                label = "Speaking fluency",
                changeText = "+${(avgFluency - 70).coerceAtLeast(5)}%",
                isPositive = true
            ),
            ImprovementStat(
                label = "Grammar consistency",
                changeText = "+${(avgGrammar - 60).coerceAtLeast(4)}%",
                isPositive = true
            ),
            ImprovementStat(
                label = "Vocabulary variety",
                changeText = "+${(avgVocabulary - 65).coerceAtLeast(6)}%",
                isPositive = true
            )
        )

        val latestSession = sessions.firstOrNull()
        val recommendation = latestSession?.focusNext ?: "Practice daily conversations to maintain your speaking streak!"

        return ProgressData(
            totalMinutes = totalMinutes,
            sessionCount = sessionCount,
            currentStreak = currentStreak,
            longestStreak = longestStreak,
            weeklyData = weeklyData,
            averageFluency = avgFluency,
            averageGrammar = avgGrammar,
            averagePronunciation = avgPronunciation,
            averageVocabulary = avgVocabulary,
            improvements = improvements,
            aiCoachRecommendation = recommendation
        )
    }

    private fun generateEmptyWeek(): List<WeeklyDayData> {
        val today = LocalDate.now(ZoneId.systemDefault())
        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

        return (0..6).map { offset ->
            val date = monday.plusDays(offset.toLong())
            val label = date.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercaseChar() }
            WeeklyDayData(
                dayLabel = label,
                minutes = 0,
                sessionCount = 0,
                fraction = 0.15f,
                isToday = (date == today)
            )
        }
    }
}
