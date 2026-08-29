package com.vaniflow.app.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.vaniflow.app.data.local.db.entity.ConversationTurnEntity
import com.vaniflow.app.data.local.db.entity.GuestProfileEntity
import com.vaniflow.app.data.local.db.entity.SavedVocabularyEntity
import com.vaniflow.app.data.local.db.entity.SessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GuestProfileDao {
    @Query("SELECT * FROM guest_profile WHERE id = 'guest_user' LIMIT 1")
    fun getProfile(): Flow<GuestProfileEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveProfile(profile: GuestProfileEntity)
}

@Dao
interface SessionDao {
    @Query("SELECT * FROM sessions ORDER BY startTimeMs DESC")
    fun getAllSessions(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE id = :sessionId LIMIT 1")
    suspend fun getSessionById(sessionId: String): SessionEntity?

    @Query("SELECT * FROM sessions WHERE id = :sessionId LIMIT 1")
    fun getSessionFlowById(sessionId: String): Flow<SessionEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: SessionEntity)

    @Query("DELETE FROM sessions")
    suspend fun deleteAllSessions()
}

@Dao
interface ConversationTurnDao {
    @Query("SELECT * FROM conversation_turns WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getTurnsForSession(sessionId: String): Flow<List<ConversationTurnEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTurns(turns: List<ConversationTurnEntity>)

    @Query("DELETE FROM conversation_turns")
    suspend fun deleteAllTurns()
}

@Dao
interface SavedVocabularyDao {
    @Query("SELECT * FROM saved_vocabulary ORDER BY savedAt DESC")
    fun getAllVocabulary(): Flow<List<SavedVocabularyEntity>>

    @Query("SELECT COUNT(*) FROM saved_vocabulary")
    fun getVocabularyCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVocabulary(vocab: SavedVocabularyEntity)

    @Query("DELETE FROM saved_vocabulary WHERE word = :word")
    suspend fun deleteVocabulary(word: String)

    @Query("DELETE FROM saved_vocabulary")
    suspend fun deleteAllVocabulary()
}
