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

@Dao
interface LearnerProfileDao {
    @Query("SELECT * FROM learner_profile WHERE id = 'default_learner_profile' LIMIT 1")
    suspend fun getProfile(): com.vaniflow.app.data.local.db.entity.LearnerProfileEntity?

    @Query("SELECT * FROM learner_profile WHERE id = 'default_learner_profile' LIMIT 1")
    fun getProfileFlow(): Flow<com.vaniflow.app.data.local.db.entity.LearnerProfileEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveProfile(profile: com.vaniflow.app.data.local.db.entity.LearnerProfileEntity)

    @Query("DELETE FROM learner_profile")
    suspend fun deleteProfile()
}

@Dao
interface DailyUsageDao {
    @Query("SELECT * FROM daily_usage WHERE date = :date LIMIT 1")
    suspend fun getUsageByDate(date: String): com.vaniflow.app.data.local.db.entity.DailyUsageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveUsage(usage: com.vaniflow.app.data.local.db.entity.DailyUsageEntity)

    @Query("SELECT * FROM daily_usage ORDER BY date DESC")
    fun getAllUsage(): Flow<List<com.vaniflow.app.data.local.db.entity.DailyUsageEntity>>

    @Query("DELETE FROM daily_usage")
    suspend fun deleteAllUsage()
}

@Dao
interface LearningEventDao {
    @Query("SELECT * FROM learning_events ORDER BY timestampEpochMs DESC")
    fun getAllEvents(): Flow<List<com.vaniflow.app.data.local.db.entity.LearningEventEntity>>

    @Query("SELECT * FROM learning_events WHERE sessionId = :sessionId ORDER BY timestampEpochMs ASC")
    suspend fun getEventsForSession(sessionId: String): List<com.vaniflow.app.data.local.db.entity.LearningEventEntity>

    @Query("SELECT * FROM learning_events WHERE conceptId = :conceptId ORDER BY timestampEpochMs DESC LIMIT 20")
    suspend fun getRecentEventsForConcept(conceptId: String): List<com.vaniflow.app.data.local.db.entity.LearningEventEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: com.vaniflow.app.data.local.db.entity.LearningEventEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvents(events: List<com.vaniflow.app.data.local.db.entity.LearningEventEntity>)

    @Query("DELETE FROM learning_events")
    suspend fun deleteAllEvents()
}

@Dao
interface ConceptMasteryDao {
    @Query("SELECT * FROM concept_mastery ORDER BY practicePriority DESC")
    fun getAllMasteryFlow(): Flow<List<com.vaniflow.app.data.local.db.entity.ConceptMasteryEntity>>

    @Query("SELECT * FROM concept_mastery")
    suspend fun getAllMastery(): List<com.vaniflow.app.data.local.db.entity.ConceptMasteryEntity>

    @Query("SELECT * FROM concept_mastery WHERE conceptId = :conceptId LIMIT 1")
    suspend fun getMastery(conceptId: String): com.vaniflow.app.data.local.db.entity.ConceptMasteryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveMastery(mastery: com.vaniflow.app.data.local.db.entity.ConceptMasteryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveAllMastery(list: List<com.vaniflow.app.data.local.db.entity.ConceptMasteryEntity>)

    @Query("DELETE FROM concept_mastery")
    suspend fun deleteAllMastery()
}

@Dao
interface VocabularyMemoryDao {
    @Query("SELECT * FROM vocabulary_memory ORDER BY lastUsedEpochMs DESC")
    fun getAllVocabularyMemoryFlow(): Flow<List<com.vaniflow.app.data.local.db.entity.VocabularyMemoryEntity>>

    @Query("SELECT * FROM vocabulary_memory ORDER BY familiarityScore ASC")
    suspend fun getExpressionsNeedingPractice(): List<com.vaniflow.app.data.local.db.entity.VocabularyMemoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVocabularyMemory(entity: com.vaniflow.app.data.local.db.entity.VocabularyMemoryEntity)

    @Query("DELETE FROM vocabulary_memory WHERE id = :id")
    suspend fun deleteVocabularyMemory(id: String)

    @Query("DELETE FROM vocabulary_memory")
    suspend fun deleteAllVocabularyMemory()
}
