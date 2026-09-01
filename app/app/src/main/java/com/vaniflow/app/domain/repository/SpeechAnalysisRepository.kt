package com.vaniflow.app.domain.repository

import com.vaniflow.app.data.local.db.entity.SpeechAnalysisEntity
import kotlinx.coroutines.flow.Flow

interface SpeechAnalysisRepository {
    fun getAllSpeechAnalysisFlow(): Flow<List<SpeechAnalysisEntity>>
    suspend fun getSpeechAnalysisForSession(sessionId: String): List<SpeechAnalysisEntity>
    suspend fun getSpeechAnalysisForTurn(turnId: String): SpeechAnalysisEntity?
    suspend fun recordSpeechAnalysis(entity: SpeechAnalysisEntity)
    suspend fun recordAllSpeechAnalysis(list: List<SpeechAnalysisEntity>)
    suspend fun clearAllSpeechAnalysis()
}
