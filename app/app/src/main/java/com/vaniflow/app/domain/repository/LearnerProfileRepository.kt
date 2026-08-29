package com.vaniflow.app.domain.repository

import com.vaniflow.app.engine.learning.tutor.LearnerProfile
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for persistent storage and retrieval of learner profile data.
 */
interface LearnerProfileRepository {
    suspend fun getLearnerProfile(): LearnerProfile
    suspend fun saveLearnerProfile(profile: LearnerProfile)
    fun observeLearnerProfile(): Flow<LearnerProfile?>
    suspend fun clearLearnerProfile()
}
