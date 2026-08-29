package com.vaniflow.app.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.vaniflow.app.data.local.db.dao.AICacheDao
import com.vaniflow.app.data.local.db.dao.ConversationTurnDao
import com.vaniflow.app.data.local.db.dao.DailyUsageDao
import com.vaniflow.app.data.local.db.dao.GuestProfileDao
import com.vaniflow.app.data.local.db.dao.LearnerProfileDao
import com.vaniflow.app.data.local.db.dao.SavedVocabularyDao
import com.vaniflow.app.data.local.db.dao.SessionDao
import com.vaniflow.app.data.local.db.entity.AICacheEntity
import com.vaniflow.app.data.local.db.entity.ConversationTurnEntity
import com.vaniflow.app.data.local.db.entity.DailyUsageEntity
import com.vaniflow.app.data.local.db.entity.GuestProfileEntity
import com.vaniflow.app.data.local.db.entity.LearnerProfileEntity
import com.vaniflow.app.data.local.db.entity.SavedVocabularyEntity
import com.vaniflow.app.data.local.db.entity.SessionEntity

@Database(
    entities = [
        GuestProfileEntity::class,
        SessionEntity::class,
        ConversationTurnEntity::class,
        SavedVocabularyEntity::class,
        AICacheEntity::class,
        LearnerProfileEntity::class,
        DailyUsageEntity::class
    ],
    version = 2,
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
    }
}
