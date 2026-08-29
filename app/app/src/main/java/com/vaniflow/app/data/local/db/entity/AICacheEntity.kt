package com.vaniflow.app.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room Entity representing a cached AI response.
 */
@Entity(
    tableName = "ai_response_cache",
    indices = [
        Index(value = ["cacheKey"], unique = true),
        Index(value = ["lastAccessedAt"]),
        Index(value = ["expiresAt"])
    ]
)
data class AICacheEntity(
    @PrimaryKey
    val cacheKey: String,
    val category: String, // KNOWLEDGE_CACHE, CONVERSATION_CACHE
    val characterId: String,
    val scenarioId: String,
    val promptVersion: String,
    val normalizedInput: String,
    val responseText: String,
    val tokensCount: Int,
    val createdAt: Long = System.currentTimeMillis(),
    val lastAccessedAt: Long = System.currentTimeMillis(),
    val expiresAt: Long
)
