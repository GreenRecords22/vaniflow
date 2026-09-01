package com.vaniflow.app.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.vaniflow.app.data.local.db.entity.SpeechAnalysisEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SpeechAnalysisDao {
    @Query("SELECT * FROM speech_analysis ORDER BY timestampEpochMs DESC")
    fun getAllSpeechAnalysisFlow(): Flow<List<SpeechAnalysisEntity>>

    @Query("SELECT * FROM speech_analysis WHERE sessionId = :sessionId ORDER BY timestampEpochMs ASC")
    suspend fun getSpeechAnalysisForSession(sessionId: String): List<SpeechAnalysisEntity>

    @Query("SELECT * FROM speech_analysis WHERE turnId = :turnId LIMIT 1")
    suspend fun getSpeechAnalysisForTurn(turnId: String): SpeechAnalysisEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSpeechAnalysis(entity: SpeechAnalysisEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllSpeechAnalysis(list: List<SpeechAnalysisEntity>)

    @Query("DELETE FROM speech_analysis")
    suspend fun deleteAllSpeechAnalysis()
}
