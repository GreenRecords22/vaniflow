package com.vaniflow.app.engine.learning.tutor.model

data class SessionLearningSummary(
    val sessionId: String,
    val durationSeconds: Long,
    val speakingMinutes: Int,
    val userTurnsCount: Int,
    val correctionsCount: Int,
    val successfulRetriesCount: Int,
    val conceptsMasteredOrImproved: List<String>,
    val conceptsNeedingPractice: List<String>,
    val newExpressionsLearned: List<String>,
    val fluencyScore: Int,
    val grammarScore: Int,
    val pronunciationScore: Int = 0,
    val vocabularyScore: Int = 0,
    val clarityRating: String = "Clear", // "Clear", "Developing", "Natural"
    val confidenceTrend: String = "Steady", // "Improving", "Steady", "Developing"
    val pronunciationEvidenceState: String = "Not enough evidence yet",
    val pronunciationPracticeAreas: List<String> = emptyList(),
    val averageWordsPerMinute: Float = 0f,
    val pauseCount: Int = 0
)
