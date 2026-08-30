package com.vaniflow.app.engine.ai.routing

import com.vaniflow.app.engine.ai.ContextManager
import com.vaniflow.app.engine.ai.analytics.DailyConversationUsageTracker
import com.vaniflow.app.engine.ai.cache.AIResponseCache
import com.vaniflow.app.engine.ai.cache.CacheCategory
import com.vaniflow.app.engine.ai.memory.ConversationMemoryManager
import com.vaniflow.app.engine.ai.provider.AIProvider
import com.vaniflow.app.engine.ai.provider.ProviderRegistry
import javax.inject.Inject
import javax.inject.Singleton

enum class ResponseDecisionType {
    CACHE_EXACT,
    CACHE_SEMANTIC,
    MEMORY_ONLY,
    REMOTE_AI_REQUIRED,
    LOCAL_AI_REQUIRED,
    FALLBACK_REQUIRED
}

data class ResponseDecision(
    val type: ResponseDecisionType,
    val directResponse: String? = null,
    val reason: String,
    val estimatedTokensAvoided: Int = 0,
    val isSensitive: Boolean = false,
    val selectedProvider: AIProvider? = null
)

/**
 * Intelligent decision engine that determines whether an expensive Remote AI API call
 * is genuinely necessary or whether safe local cache/memory can answer with 0 API cost.
 */
@Singleton
class SmartResponseDecisionEngine @Inject constructor(
    private val memoryManager: ConversationMemoryManager,
    private val responseCache: AIResponseCache,
    private val usageTracker: DailyConversationUsageTracker,
    private val providerRegistry: ProviderRegistry
) {

    private val sensitivePatterns = listOf(
        Regex("(?i)\\b(password|pin|otp|cvv|secret|token)\\b"),
        Regex("\\b\\d{4}[- ]?\\d{4}[- ]?\\d{4}[- ]?\\d{4}\\b"), // Credit card
        Regex("\\b\\d{12}\\b"), // Aadhaar / 12-digit ID
        Regex("\\b\\d{3}-\\d{2}-\\d{4}\\b") // SSN
    )

    suspend fun evaluateDecision(
        userInput: String,
        characterId: String,
        scenarioId: String
    ): ResponseDecision {
        val trimmed = userInput.trim()
        val lower = trimmed.lowercase()

        // 1. Sensitive / Private Data Protection Check
        val isSensitive = sensitivePatterns.any { it.containsMatchIn(trimmed) }

        // 2. Direct Session Memory Query Check (Avoids API)
        val memoryAnswer = memoryManager.findMemoryAnswer(trimmed)
        if (memoryAnswer != null && !isSensitive) {
            val tokensAvoided = ContextManager.estimateTokenCount(memoryAnswer) + ContextManager.estimateTokenCount(trimmed) + 80
            return ResponseDecision(
                type = ResponseDecisionType.MEMORY_ONLY,
                directResponse = memoryAnswer,
                reason = "Answered directly from session memory facts",
                estimatedTokensAvoided = tokensAvoided,
                isSensitive = false
            )
        }

        // 3. Educational / Definitional Query Check (Safe Reusable Knowledge)
        val definitionMatch = checkEducationalDefinition(trimmed)
        if (definitionMatch != null && !isSensitive) {
            val tokensAvoided = ContextManager.estimateTokenCount(definitionMatch) + 60
            return ResponseDecision(
                type = ResponseDecisionType.CACHE_EXACT,
                directResponse = definitionMatch,
                reason = "Answered from local educational knowledge dictionary",
                estimatedTokensAvoided = tokensAvoided,
                isSensitive = false
            )
        }

        // 4. Stable Factual Knowledge Cache Check
        if (isFactualKnowledgeQuery(lower) && !isSensitive) {
            val cached = responseCache.getCachedResponse(
                category = CacheCategory.KNOWLEDGE_CACHE,
                characterId = characterId,
                scenarioId = scenarioId,
                userInput = trimmed,
                contextHash = "global_knowledge",
                promptVersion = "1.0"
            )
            if (cached != null && !cached.isExpired) {
                val tokensAvoided = cached.estimatedTokens + ContextManager.estimateTokenCount(trimmed) + 50
                return ResponseDecision(
                    type = ResponseDecisionType.CACHE_EXACT,
                    directResponse = cached.text,
                    reason = "Exact match hit in safe knowledge cache",
                    estimatedTokensAvoided = tokensAvoided,
                    isSensitive = false
                )
            }
        }

        // 5. 90-Minute Daily Policy Evaluation (Fair-Use Protection)
        if (usageTracker.isFairUseExceeded()) {
            val localProvider = providerRegistry.allProviders.firstOrNull {
                (it.providerId == "local_vaniflow" || it.priority == 3) && it.isAvailable()
            }
            return if (localProvider != null) {
                ResponseDecision(
                    type = ResponseDecisionType.LOCAL_AI_REQUIRED,
                    reason = "Daily 90-minute limit reached; transitioning to local on-device SLM",
                    isSensitive = isSensitive,
                    selectedProvider = localProvider
                )
            } else {
                val fallback = providerRegistry.allProviders.firstOrNull { it.priority >= 4 || it.providerId == "fallback_contextual" }
                    ?: providerRegistry.allProviders.lastOrNull()
                ResponseDecision(
                    type = ResponseDecisionType.FALLBACK_REQUIRED,
                    reason = "Daily 90-minute cloud limit reached; transitioning to conversational fallback",
                    isSensitive = isSensitive,
                    selectedProvider = fallback
                )
            }
        }

        // 6. Healthy Multi-Provider Routing Hierarchy
        val healthyProviders = providerRegistry.getHealthyProviders()
        val primaryRemote = healthyProviders.firstOrNull { it.priority == 1 }
        val secondaryRemote = healthyProviders.firstOrNull { it.priority == 2 }
        val localSlm = healthyProviders.firstOrNull { it.priority == 3 }

        return when {
            primaryRemote != null -> ResponseDecision(
                type = ResponseDecisionType.REMOTE_AI_REQUIRED,
                reason = "Routed to Primary Remote AI for fresh contextual reasoning",
                isSensitive = isSensitive,
                selectedProvider = primaryRemote
            )
            secondaryRemote != null -> ResponseDecision(
                type = ResponseDecisionType.REMOTE_AI_REQUIRED,
                reason = "Primary remote degraded/warning; failover to Secondary Remote AI",
                isSensitive = isSensitive,
                selectedProvider = secondaryRemote
            )
            localSlm != null -> ResponseDecision(
                type = ResponseDecisionType.LOCAL_AI_REQUIRED,
                reason = "Remote AI unavailable; falling back to local on-device SLM",
                isSensitive = isSensitive,
                selectedProvider = localSlm
            )
            else -> ResponseDecision(
                type = ResponseDecisionType.FALLBACK_REQUIRED,
                reason = "All primary and secondary AI engines unavailable; using contextual fallback",
                isSensitive = isSensitive,
                selectedProvider = providerRegistry.allProviders.lastOrNull()
            )
        }
    }

    private fun isFactualKnowledgeQuery(lower: String): Boolean {
        return lower.startsWith("what is the capital") ||
                lower.startsWith("capital of") ||
                lower.startsWith("who is the president") ||
                lower.startsWith("what are the vowels") ||
                lower.startsWith("how many days in")
    }

    private fun checkEducationalDefinition(input: String): String? {
        val lower = input.lowercase().trim()
        return when {
            lower.contains("what does although mean") || lower.contains("define although") ->
                "Although means in spite of the fact that; even though (conjunction). For example: \"Although it was raining, we went for a walk.\""
            lower.contains("what does confident mean") || lower.contains("define confident") ->
                "Confident means feeling or showing certainty about your abilities or qualities (adjective, /ˈkɒnfɪdənt/). For example: \"She felt confident before her presentation.\""
            lower.contains("what does articulate mean") || lower.contains("define articulate") ->
                "Articulate means having or showing the ability to speak fluently and coherently (adjective/verb, /ɑːˈtɪkjʊlət/). For example: \"He gave an articulate explanation.\""
            lower.contains("what are the vowels") || lower.contains("vowels in english") ->
                "The primary vowels in the English alphabet are A, E, I, O, and U (with Y sometimes acting as a vowel sound)."
            lower == "what is the capital of india" || lower == "capital of india" || lower == "what is india's capital" ->
                "The capital of India is New Delhi."
            else -> null
        }
    }
}