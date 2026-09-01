package com.vaniflow.app.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.vaniflow.app.data.local.db.dao.AICacheDao
import com.vaniflow.app.data.local.db.dao.ConceptMasteryDao
import com.vaniflow.app.data.local.db.dao.ConversationTurnDao
import com.vaniflow.app.data.local.db.dao.DailyUsageDao
import com.vaniflow.app.data.local.db.dao.GuestProfileDao
import com.vaniflow.app.data.local.db.dao.LearnerProfileDao
import com.vaniflow.app.data.local.db.dao.LearningEventDao
import com.vaniflow.app.data.local.db.dao.SavedVocabularyDao
import com.vaniflow.app.data.local.db.dao.SessionDao
import com.vaniflow.app.data.local.db.dao.SpeechAnalysisDao
import com.vaniflow.app.data.local.db.dao.VocabularyMemoryDao
import com.vaniflow.app.data.local.db.entity.AICacheEntity
import com.vaniflow.app.data.local.db.entity.ConceptMasteryEntity
import com.vaniflow.app.data.local.db.entity.ConversationTurnEntity
import com.vaniflow.app.data.local.db.entity.DailyUsageEntity
import com.vaniflow.app.data.local.db.entity.GuestProfileEntity
import com.vaniflow.app.data.local.db.entity.LearnerProfileEntity
import com.vaniflow.app.data.local.db.entity.LearningEventEntity
import com.vaniflow.app.data.local.db.entity.SavedVocabularyEntity
import com.vaniflow.app.data.local.db.entity.SessionEntity
import com.vaniflow.app.data.local.db.entity.SpeechAnalysisEntity
import com.vaniflow.app.data.local.db.entity.VocabularyMemoryEntity

@Database(
    entities = [
        GuestProfileEntity::class,
        SessionEntity::class,
        ConversationTurnEntity::class,
        SavedVocabularyEntity::class,
        AICacheEntity::class,
        LearnerProfileEntity::class,
        DailyUsageEntity::class,
        LearningEventEntity::class,
        ConceptMasteryEntity::class,
        VocabularyMemoryEntity::class,
        SpeechAnalysisEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class VaniFlowDatabase : RoomDatabase() {
    abstract fun guestProfileDao(): GuestProfileDao
    abstract fun sessionDao(): SessionDao
    abstract fun conversationTurnDao(): ConversationTurnDao
    abstract fun savedVocabularyDao(): SavedVocabularyDao
    abstract fun aiCacheDao(): AICacheDao
    abstract fun learnerProfileDao(): LearnerProfileDao
    abstract fun dailyUsageDao(): DailyUsageDao
    abstract fun learningEventDao(): LearningEventDao
    abstract fun conceptMasteryDao(): ConceptMasteryDao
    abstract fun vocabularyMemoryDao(): VocabularyMemoryDao
    abstract fun speechAnalysisDao(): SpeechAnalysisDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `learner_profile` (
                        `id` TEXT NOT NULL,
                        `estimatedLevel` TEXT NOT NULL,
                        `speakingConfidenceScore` REAL NOT NULL,
                        `totalUtterances` INTEGER NOT NULL,
                        `correctionsDelivered` INTEGER NOT NULL,
                        `successfulRetries` INTEGER NOT NULL,
                        `commonMistakesJson` TEXT NOT NULL,
                        `masteredConceptsJson` TEXT NOT NULL,
                        `conceptsNeedingPracticeJson` TEXT NOT NULL,
                        `recentCorrectionsJson` TEXT NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `daily_usage` (
                        `date` TEXT NOT NULL,
                        `speakingSeconds` INTEGER NOT NULL,
                        `inputTokens` INTEGER NOT NULL,
                        `outputTokens` INTEGER NOT NULL,
                        `totalRequests` INTEGER NOT NULL,
                        `cacheHits` INTEGER NOT NULL,
                        `savedTokens` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`date`)
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `learning_events` (
                        `id` TEXT NOT NULL,
                        `type` TEXT NOT NULL,
                        `conceptId` TEXT NOT NULL,
                        `category` TEXT NOT NULL,
                        `severity` TEXT NOT NULL,
                        `originalUtterance` TEXT,
                        `correctedForm` TEXT,
                        `isSuccess` INTEGER NOT NULL,
                        `sessionId` TEXT,
                        `confidenceImpact` REAL NOT NULL,
                        `timestampEpochMs` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `concept_mastery` (
                        `conceptId` TEXT NOT NULL,
                        `category` TEXT NOT NULL,
                        `masteryScore` INTEGER NOT NULL,
                        `attemptCount` INTEGER NOT NULL,
                        `successCount` INTEGER NOT NULL,
                        `failureCount` INTEGER NOT NULL,
                        `consecutiveSuccesses` INTEGER NOT NULL,
                        `consecutiveFailures` INTEGER NOT NULL,
                        `lastPracticedEpochMs` INTEGER NOT NULL,
                        `lastSuccessEpochMs` INTEGER NOT NULL,
                        `practicePriority` INTEGER NOT NULL,
                        PRIMARY KEY(`conceptId`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `vocabulary_memory` (
                        `id` TEXT NOT NULL,
                        `wordOrPhrase` TEXT NOT NULL,
                        `phonetic` TEXT NOT NULL,
                        `partOfSpeech` TEXT NOT NULL,
                        `meaning` TEXT NOT NULL,
                        `exampleSentence` TEXT NOT NULL,
                        `familiarityScore` INTEGER NOT NULL,
                        `usageCount` INTEGER NOT NULL,
                        `lastUsedEpochMs` INTEGER NOT NULL,
                        `sourceScenarioId` TEXT,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `speech_analysis` (
                        `id` TEXT NOT NULL,
                        `turnId` TEXT NOT NULL,
                        `sessionId` TEXT NOT NULL,
                        `audioDurationMs` INTEGER NOT NULL,
                        `voicedDurationMs` INTEGER NOT NULL,
                        `pauseCount` INTEGER NOT NULL,
                        `totalPauseDurationMs` INTEGER NOT NULL,
                        `wordsPerMinute` REAL NOT NULL,
                        `qualitativeFluency` TEXT NOT NULL,
                        `qualitativePronunciation` TEXT NOT NULL,
                        `hesitationType` TEXT NOT NULL,
                        `snrDb` REAL NOT NULL,
                        `hasPhonemeEvidence` INTEGER NOT NULL,
                        `practicedSound` TEXT,
                        `timestampEpochMs` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
            }
        }
    }
}
