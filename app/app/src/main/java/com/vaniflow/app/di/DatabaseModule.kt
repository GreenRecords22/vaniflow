package com.vaniflow.app.di

import android.content.Context
import androidx.room.Room
import com.vaniflow.app.data.local.db.VaniFlowDatabase
import com.vaniflow.app.data.local.db.dao.AICacheDao
import com.vaniflow.app.data.local.db.dao.ConversationTurnDao
import com.vaniflow.app.data.local.db.dao.GuestProfileDao
import com.vaniflow.app.data.local.db.dao.SavedVocabularyDao
import com.vaniflow.app.data.local.db.dao.SessionDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): VaniFlowDatabase {
        return Room.databaseBuilder(
            context,
            VaniFlowDatabase::class.java,
            "vaniflow.db"
        ).fallbackToDestructiveMigration().build()
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
}
