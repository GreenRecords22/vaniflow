package com.vaniflow.app.domain.repository

import kotlinx.coroutines.flow.Flow

data class WeeklyDayData(
    val dayLabel: String,
    val minutes: Int,
    val sessionCount: Int,
    val fraction: Float,
    val isToday: Boolean = false
)

data class ImprovementStat(
    val label: String,
    val changeText: String,
    val isPositive: Boolean
)

data class ProgressData(
    val totalMinutes: Int,
    val sessionCount: Int,
    val currentStreak: Int,
    val longestStreak: Int,
    val weeklyData: List<WeeklyDayData>,
    val averageFluency: Int,
    val averageGrammar: Int,
    val averagePronunciation: Int,
    val averageVocabulary: Int,
    val improvements: List<ImprovementStat>,
    val aiCoachRecommendation: String,
    val estimatedLevel: String = "Building level...",
    val speakingConfidenceScore: Float = 65.0f,
    val masteredConceptsCount: Int = 0,
    val conceptsNeedingPracticeCount: Int = 0,
    val vocabularyCount: Int = 0,
    val conceptsNeedingPractice: List<String> = emptyList()
)

interface ProgressRepository {
    fun getProgress(): Flow<ProgressData>
    fun calculateStreak(sessionTimestamps: List<Long>, nowEpochMs: Long = System.currentTimeMillis()): Pair<Int, Int>
}
