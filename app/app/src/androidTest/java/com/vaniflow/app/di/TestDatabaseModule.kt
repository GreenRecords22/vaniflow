package com.vaniflow.app.di

import android.content.Context
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.vaniflow.app.data.local.db.VaniFlowDatabase
import com.vaniflow.app.data.local.db.dao.AICacheDao
import com.vaniflow.app.data.local.db.dao.ConversationTurnDao
import com.vaniflow.app.data.local.db.dao.GuestProfileDao
import com.vaniflow.app.data.local.db.dao.SavedVocabularyDao
import com.vaniflow.app.data.local.db.dao.SessionDao
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton

/**
 * Test Hilt module that overrides the production database with an in-memory Room instance.
 * This ensures instrumented tests never touch production data.
 */
@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [DatabaseModule::class]
)
object TestDatabaseModule {

    @Provides
    @Singleton
    fun provideTestDatabase(): VaniFlowDatabase {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return Room.inMemoryDatabaseBuilder(context, VaniFlowDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @Provides
    fun provideGuestProfileDao(database: VaniFlowDatabase): GuestProfileDao {
        return database.guestProfileDao()
    }

    @Provides
    fun provideSessionDao(database: VaniFlowDatabase): SessionDao {
        return database.sessionDao()
    }

    @Provides
    fun provideConversationTurnDao(database: VaniFlowDatabase): ConversationTurnDao {
        return database.conversationTurnDao()
    }

    @Provides
    fun provideSavedVocabularyDao(database: VaniFlowDatabase): SavedVocabularyDao {
        return database.savedVocabularyDao()
    }

    @Provides
    fun provideAICacheDao(database: VaniFlowDatabase): AICacheDao {
        return database.aiCacheDao()
    }

    @Provides
    fun provideLearnerProfileDao(database: VaniFlowDatabase): com.vaniflow.app.data.local.db.dao.LearnerProfileDao {
        return database.learnerProfileDao()
    }

    @Provides
    fun provideDailyUsageDao(database: VaniFlowDatabase): com.vaniflow.app.data.local.db.dao.DailyUsageDao {
        return database.dailyUsageDao()
    }

    @Provides
    fun provideLearningEventDao(database: VaniFlowDatabase): com.vaniflow.app.data.local.db.dao.LearningEventDao {
        return database.learningEventDao()
    }

    @Provides
    fun provideConceptMasteryDao(database: VaniFlowDatabase): com.vaniflow.app.data.local.db.dao.ConceptMasteryDao {
        return database.conceptMasteryDao()
    }

    @Provides
    fun provideVocabularyMemoryDao(database: VaniFlowDatabase): com.vaniflow.app.data.local.db.dao.VocabularyMemoryDao {
        return database.vocabularyMemoryDao()
    }
}
