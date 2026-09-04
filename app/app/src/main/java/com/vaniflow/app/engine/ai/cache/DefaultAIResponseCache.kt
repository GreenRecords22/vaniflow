package com.vaniflow.app.engine.ai.cache

import com.vaniflow.app.data.local.db.dao.AICacheDao
import com.vaniflow.app.data.local.db.entity.AICacheEntity
import com.vaniflow.app.engine.ai.AIRoutingLevel
import com.vaniflow.app.engine.ai.ContextManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultAIResponseCache @Inject constructor(
    private val aiCacheDao: AICacheDao
) : AIResponseCache {

    companion object {
        const val MAX_CACHE_ENTRIES = 500
        const val KNOWLEDGE_TTL_MS = 7 * 24 * 60 * 60 * 1000L // 7 Days
        const val CONVERSATION_TTL_MS = 6 * 60 * 60 * 1000L   // 6 Hours
    }

    private val _usageStats = MutableStateFlow(AIUsageStats())
    override val usageStats: StateFlow<AIUsageStats> = _usageStats.asStateFlow()

    override suspend fun getCachedResponse(
        category: CacheCategory,
        characterId: String,
        scenarioId: String,
        userInput: String,
        contextHash: String,
        promptVersion: String
    ): CachedAIResponse? = withContext(Dispatchers.IO) {
        if (category == CacheCategory.DO_NOT_CACHE) {
            return@withContext null
        }

        try {
            val key = generateCacheKey(category, characterId, scenarioId, userInput, contextHash, promptVersion)
            val entity = aiCacheDao.getByKey(key)

            if (entity == null) {
                val current = _usageStats.value
                _usageStats.value = current.copy(cacheMisses = current.cacheMisses + 1)
                return@withContext null
            }

            val now = System.currentTimeMillis()
            if (entity.expiresAt < now) {
                // Expired
                val current = _usageStats.value
                _usageStats.value = current.copy(cacheMisses = current.cacheMisses + 1)
                return@withContext null
            }

            // Cache Hit
            aiCacheDao.updateAccessTime(key, now)
            val savedTokens = entity.tokensCount
            val current = _usageStats.value
            _usageStats.value = current.copy(
                cacheHits = current.cacheHits + 1,
                cloudRequestsAvoided = current.cloudRequestsAvoided + 1,
                estimatedTokensSaved = current.estimatedTokensSaved + savedTokens
            )

            CachedAIResponse(
                cacheKey = entity.cacheKey,
                text = entity.responseText,
                category = category,
                estimatedTokens = entity.tokensCount,
                isExpired = false
            )
        } catch (_: Exception) {
            // Database failure resilience - treat as cache miss
            null
        }
    }

    override suspend fun putResponse(
        category: CacheCategory,
        characterId: String,
        scenarioId: String,
        userInput: String,
        responseText: String,
        contextHash: String,
        promptVersion: String
    ) = withContext(Dispatchers.IO) {
        if (category == CacheCategory.DO_NOT_CACHE || responseText.isBlank()) {
            return@withContext
        }

        try {
            val now = System.currentTimeMillis()
            val ttl = when (category) {
                CacheCategory.KNOWLEDGE_CACHE -> KNOWLEDGE_TTL_MS
                CacheCategory.CONVERSATION_CACHE -> CONVERSATION_TTL_MS
                CacheCategory.DO_NOT_CACHE -> 0L
            }

            val key = generateCacheKey(category, characterId, scenarioId, userInput, contextHash, promptVersion)
            val tokenCount = ContextManager.estimateTokenCount(responseText)

            // Evict oldest if limit reached
            val count = aiCacheDao.getEntryCount()
            if (count >= MAX_CACHE_ENTRIES) {
                aiCacheDao.evictOldest(count - MAX_CACHE_ENTRIES + 1)
            }

            val entity = AICacheEntity(
                cacheKey = key,
                category = category.name,
                characterId = characterId,
                scenarioId = scenarioId,
                promptVersion = promptVersion,
                normalizedInput = normalizeInput(userInput),
                responseText = responseText,
                tokensCount = tokenCount,
                createdAt = now,
                lastAccessedAt = now,
                expiresAt = now + ttl
            )

            aiCacheDao.insertOrUpdate(entity)
        } catch (_: Exception) {
            // Database failure resilience - ignore cache write failure
        }
    }

    override suspend fun recordGeneration(routingLevel: AIRoutingLevel, tokens: Int) {
        val current = _usageStats.value
        _usageStats.value = when (routingLevel) {
            AIRoutingLevel.LOCAL_SLM -> current.copy(localRequests = current.localRequests + 1)
            AIRoutingLevel.OPTIONAL_CLOUD -> current.copy(cloudRequests = current.cloudRequests + 1)
            AIRoutingLevel.LOCAL_FALLBACK, AIRoutingLevel.SCENARIO_MATRIX, AIRoutingLevel.EMERGENCY_FALLBACK ->
                current.copy(fallbackRequests = current.fallbackRequests + 1)
            AIRoutingLevel.CONTEXT_AWARE_FALLBACK -> current
            AIRoutingLevel.DETERMINISTIC_RULE -> current
        }
    }

    override suspend fun clearCache(): Int = withContext(Dispatchers.IO) {
        aiCacheDao.clearAll()
    }

    override suspend fun purgeExpired(): Int = withContext(Dispatchers.IO) {
        aiCacheDao.deleteExpired()
    }

    private fun generateCacheKey(
        category: CacheCategory,
        characterId: String,
        scenarioId: String,
        userInput: String,
        contextHash: String,
        promptVersion: String
    ): String {
        val normalized = normalizeInput(userInput)
        val raw = "${category.name}|$characterId|$scenarioId|$normalized|$contextHash|$promptVersion"
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(raw.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    private fun normalizeInput(input: String): String {
        return input.trim().lowercase().replace("\\s+".toRegex(), " ")
    }
}
