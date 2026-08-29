package com.vaniflow.app.engine.ai.cache

import kotlinx.coroutines.flow.StateFlow

enum class CacheCategory {
    KNOWLEDGE_CACHE,    // Definitions, grammar explainers, vocabulary (7 days TTL)
    CONVERSATION_CACHE, // Low-context greetings, common openers (6 hours TTL)
    DO_NOT_CACHE        // Dynamic, personal, or context-heavy dialogue (Bypass)
}

enum class CachePolicy {
    CACHE_HIT,
    CACHE_MISS,
    CACHE_BYPASS
}

data class CachedAIResponse(
    val cacheKey: String,
    val text: String,
    val category: CacheCategory,
    val estimatedTokens: Int,
    val isExpired: Boolean
)

data class AIUsageStats(
    val cacheHits: Long = 0L,
    val cacheMisses: Long = 0L,
    val cloudRequestsAvoided: Long = 0L,
    val estimatedTokensSaved: Long = 0L,
    val localRequests: Long = 0L,
    val cloudRequests: Long = 0L,
    val fallbackRequests: Long = 0L
)

/**
 * High-performance on-device AI response cache interface.
 */
interface AIResponseCache {
    val usageStats: StateFlow<AIUsageStats>

    suspend fun getCachedResponse(
        category: CacheCategory,
        characterId: String,
        scenarioId: String,
        userInput: String,
        contextHash: String = "",
        promptVersion: String = "1.0"
    ): CachedAIResponse?

    suspend fun putResponse(
        category: CacheCategory,
        characterId: String,
        scenarioId: String,
        userInput: String,
        responseText: String,
        contextHash: String = "",
        promptVersion: String = "1.0"
    )

    suspend fun recordGeneration(routingLevel: com.vaniflow.app.engine.ai.AIRoutingLevel, tokens: Int)
    suspend fun clearCache(): Int
    suspend fun purgeExpired(): Int
}
