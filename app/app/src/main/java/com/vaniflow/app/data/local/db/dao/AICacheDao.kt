package com.vaniflow.app.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.vaniflow.app.data.local.db.entity.AICacheEntity

@Dao
interface AICacheDao {

    @Query("SELECT * FROM ai_response_cache WHERE cacheKey = :key LIMIT 1")
    suspend fun getByKey(key: String): AICacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(entity: AICacheEntity)

    @Query("UPDATE ai_response_cache SET lastAccessedAt = :accessedAt WHERE cacheKey = :key")
    suspend fun updateAccessTime(key: String, accessedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM ai_response_cache WHERE expiresAt < :currentTime")
    suspend fun deleteExpired(currentTime: Long = System.currentTimeMillis()): Int

    @Query("SELECT COUNT(*) FROM ai_response_cache")
    suspend fun getEntryCount(): Int

    @Query("DELETE FROM ai_response_cache WHERE cacheKey IN (SELECT cacheKey FROM ai_response_cache ORDER BY lastAccessedAt ASC LIMIT :count)")
    suspend fun evictOldest(count: Int): Int

    @Query("DELETE FROM ai_response_cache")
    suspend fun clearAll(): Int
}
