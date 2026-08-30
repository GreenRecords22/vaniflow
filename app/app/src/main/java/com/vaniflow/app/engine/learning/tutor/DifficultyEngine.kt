package com.vaniflow.app.engine.learning.tutor

import com.vaniflow.app.engine.learning.tutor.model.DifficultyLevel
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DifficultyEngine @Inject constructor() {

    fun determineDifficulty(
        estimatedLevel: EstimatedLevel,
        speakingConfidence: Float,
        successfulRetriesCount: Int,
        totalUtterances: Int
    ): DifficultyLevel {
        val baseLevel = when (estimatedLevel) {
            EstimatedLevel.A1 -> DifficultyLevel.BEGINNER
            EstimatedLevel.A2 -> DifficultyLevel.BEGINNER
            EstimatedLevel.B1 -> DifficultyLevel.INTERMEDIATE
            EstimatedLevel.B2 -> DifficultyLevel.UPPER_INTERMEDIATE
            EstimatedLevel.C1 -> DifficultyLevel.ADVANCED
        }

        // Adaptive nudge based on confidence & retry consistency
        val retrySuccessRate = if (totalUtterances > 0) successfulRetriesCount.toFloat() / totalUtterances.toFloat() else 0f

        return if (speakingConfidence < 45f && baseLevel != DifficultyLevel.BEGINNER) {
            // Drop one tier if learner is struggling/lacking confidence
            when (baseLevel) {
                DifficultyLevel.ADVANCED -> DifficultyLevel.UPPER_INTERMEDIATE
                DifficultyLevel.UPPER_INTERMEDIATE -> DifficultyLevel.INTERMEDIATE
                else -> DifficultyLevel.BEGINNER
            }
        } else if (speakingConfidence >= 85f && retrySuccessRate >= 0.8f && totalUtterances >= 20) {
            // Nudge up if learner is exceptionally confident and accurate
            when (baseLevel) {
                DifficultyLevel.BEGINNER -> DifficultyLevel.INTERMEDIATE
                DifficultyLevel.INTERMEDIATE -> DifficultyLevel.UPPER_INTERMEDIATE
                DifficultyLevel.UPPER_INTERMEDIATE -> DifficultyLevel.ADVANCED
                DifficultyLevel.ADVANCED -> DifficultyLevel.ADVANCED
            }
        } else {
            baseLevel
        }
    }
}
