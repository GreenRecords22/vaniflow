package com.vaniflow.app.data.repository

import com.vaniflow.app.data.local.db.dao.SpeechAnalysisDao
import com.vaniflow.app.data.local.db.entity.SpeechAnalysisEntity
import com.vaniflow.app.domain.repository.SpeechAnalysisRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultSpeechAnalysisRepository @Inject constructor(
    private val speechAnalysisDao: SpeechAnalysisDao
) : SpeechAnalysisRepository {

    override fun getAllSpeechAnalysisFlow(): Flow<List<SpeechAnalysisEntity>> {
        return speechAnalysisDao.getAllSpeechAnalysisFlow()
    }

    override suspend fun getSpeechAnalysisForSession(sessionId: String): List<SpeechAnalysisEntity> {
        return speechAnalysisDao.getSpeechAnalysisForSession(sessionId)
    }

    override suspend fun getSpeechAnalysisForTurn(turnId: String): SpeechAnalysisEntity? {
        return speechAnalysisDao.getSpeechAnalysisForTurn(turnId)
    }

    override suspend fun recordSpeechAnalysis(entity: SpeechAnalysisEntity) {
        speechAnalysisDao.insertSpeechAnalysis(entity)
    }

    override suspend fun recordAllSpeechAnalysis(list: List<SpeechAnalysisEntity>) {
        speechAnalysisDao.insertAllSpeechAnalysis(list)
    }

    override suspend fun clearAllSpeechAnalysis() {
        speechAnalysisDao.deleteAllSpeechAnalysis()
    }
}
