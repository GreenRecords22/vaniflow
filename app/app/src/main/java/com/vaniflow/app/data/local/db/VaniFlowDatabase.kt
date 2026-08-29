package com.vaniflow.app.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.vaniflow.app.data.local.db.dao.AICacheDao
import com.vaniflow.app.data.local.db.dao.ConversationTurnDao
import com.vaniflow.app.data.local.db.dao.GuestProfileDao
import com.vaniflow.app.data.local.db.dao.SavedVocabularyDao
import com.vaniflow.app.data.local.db.dao.SessionDao
import com.vaniflow.app.data.local.db.entity.AICacheEntity
import com.vaniflow.app.data.local.db.entity.ConversationTurnEntity
import com.vaniflow.app.data.local.db.entity.GuestProfileEntity
import com.vaniflow.app.data.local.db.entity.SavedVocabularyEntity
import com.vaniflow.app.data.local.db.entity.SessionEntity

@Database(
    entities = [
        GuestProfileEntity::class,
        SessionEntity::class,
        ConversationTurnEntity::class,
        SavedVocabularyEntity::class,
        AICacheEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class VaniFlowDatabase : RoomDatabase() {
    abstract fun guestProfileDao(): GuestProfileDao
    abstract fun sessionDao(): SessionDao
    abstract fun conversationTurnDao(): ConversationTurnDao
    abstract fun savedVocabularyDao(): SavedVocabularyDao
    abstract fun aiCacheDao(): AICacheDao
}
