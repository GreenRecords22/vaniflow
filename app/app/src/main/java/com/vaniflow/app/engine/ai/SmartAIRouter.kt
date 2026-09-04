package com.vaniflow.app.engine.ai

import com.vaniflow.app.engine.ai.analytics.DailyConversationUsageTracker
import com.vaniflow.app.engine.ai.cache.AIResponseCache
import com.vaniflow.app.engine.ai.cache.CacheCategory
import com.vaniflow.app.engine.ai.memory.ConversationMemoryManager
import com.vaniflow.app.engine.ai.prompt.ConversationPromptBuilder
import com.vaniflow.app.engine.ai.provider.FallbackAIProvider
import com.vaniflow.app.engine.ai.provider.LocalAIProvider
import com.vaniflow.app.engine.ai.provider.ProviderHealthManager
import com.vaniflow.app.engine.ai.provider.ProviderQuotaManager
import com.vaniflow.app.engine.ai.provider.ProviderRegistry
import com.vaniflow.app.engine.ai.provider.RemoteAIProvider
import com.vaniflow.app.engine.ai.provider.SecondaryRemoteAIProvider
import com.vaniflow.app.engine.ai.routing.ResponseDecisionType
import com.vaniflow.app.engine.ai.routing.SmartResponseDecisionEngine
import com.vaniflow.app.engine.model.VaniFlowModelTier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

import com.vaniflow.app.engine.ai.guard.QualityCheckResult
import com.vaniflow.app.engine.ai.guard.ResponseQualityGuard

/**
 * Master Smart AI Router — API-First + Token Saver + Smart Memory Architecture:
 *
 * - LEVEL 0: Local Knowledge Cache / Session Memory (Zero API Cost)
 * - LEVEL 1: Primary Remote AI (VaniFlow Cloud AI)
 * - LEVEL 2: Secondary Remote AI (Cloud Failover / Quota Warning)
 * - LEVEL 3: VaniFlow Local On-Device AI (Offline / Post-90min Fair Use)
 * - LEVEL 4: Context-Aware Conversational Fallback
 * - LEVEL 5: Scenario Dialogue Matrix (Absolute Last Resort)
 */
@Singleton
class SmartAIRouter @Inject constructor(
    private val providerRegistry: ProviderRegistry,
    private val memoryManager: ConversationMemoryManager,
    private val usageTracker: DailyConversationUsageTracker,
    private val fallbackAIEngine: FallbackAIEngine,
    private val aiResponseCache: AIResponseCache,
    private val decisionEngine: SmartResponseDecisionEngine
) : AIEngine {

    private var activeCharacterId: String = "raya"
    private var activeScenarioId: String = "general"
    private val modelTier: String = VaniFlowModelTier.CORE.name
    private val promptVersion: String = "2.0"
    private val responseQualityGuard: ResponseQualityGuard = ResponseQualityGuard()

    /** Backward-compatible constructor for testing with legacy local + cloud provider arguments. */
    constructor(
        localAIEngine: LocalAIEngine,
        cloudAIProvider: CloudAIProvider,
        fallbackAIEngine: FallbackAIEngine,
        aiResponseCache: AIResponseCache
    ) : this(
        ProviderRegistry(
            listOf(
                LocalAIProvider(localAIEngine, ProviderHealthManager(ProviderQuotaManager())),
                LegacyCloudAIAdapter(cloudAIProvider),
                FallbackAIProvider(ContextAwareFallbackEngine())
            )
        ),
        ConversationMemoryManager(),
        DailyConversationUsageTracker(),
        fallbackAIEngine,
        aiResponseCache
    )

    /** Backward-compatible constructor for testing. */
    constructor(
        providerRegistry: ProviderRegistry,
        memoryManager: ConversationMemoryManager,
        usageTracker: DailyConversationUsageTracker,
        fallbackAIEngine: FallbackAIEngine,
        aiResponseCache: AIResponseCache
    ) : this(
        providerRegistry,
        memoryManager,
        usageTracker,
        fallbackAIEngine,
        aiResponseCache,
        SmartResponseDecisionEngine(memoryManager, aiResponseCache, usageTracker, providerRegistry)
    )

    override fun setActiveContext(characterId: String, scenarioId: String) {
        activeCharacterId = characterId
        activeScenarioId = scenarioId
        RepetitionGuard.reset()
        memoryManager.clear()
    }

    override suspend fun generateResponse(
        systemPrompt: String,
        conversationHistory: List<AITurn>,
        userInput: String
    ): AIResult {
        val memoryPrompt = memoryManager.buildMemoryPrompt(systemPrompt)
        val rollingHistory = memoryManager.getRollingTurns().ifEmpty {
            ContextManager.compactHistory(conversationHistory)
        }

        // Level 0: Smart Token-Saving Decision (Direct Memory / Safe Factual Cache)
        val decision = decisionEngine.evaluateDecision(userInput, activeCharacterId, activeScenarioId)
        if (decision.type == ResponseDecisionType.MEMORY_ONLY ||
            decision.type == ResponseDecisionType.CACHE_EXACT ||
            decision.type == ResponseDecisionType.CACHE_SEMANTIC
        ) {
            val direct = decision.directResponse ?: "Here is what I remember."
            val tokensSaved = decision.estimatedTokensAvoided
            usageTracker.recordTurn("cache_memory_saver", 10, tokensSaved, 5, isCacheHit = true)
            memoryManager.addTurn("user", userInput)
            memoryManager.addTurn("assistant", direct)
            return AIResult.Success(
                text = direct,
                latencyMs = 5,
                metadata = AIResponseMetadata(
                    routingLevel = AIRoutingLevel.DETERMINISTIC_RULE,
                    latencyMs = 5,
                    tokensGenerated = tokensSaved,
                    providerName = if (decision.type == ResponseDecisionType.MEMORY_ONLY) "Session Memory Engine" else "Local Knowledge Cache"
                )
            )
        }

        // Multi-Provider Cascade: Remote Primary -> Remote Secondary -> Local VaniFlow -> Context Fallback
        val providers = if (usageTracker.isFairUseExceeded() || decision.type == ResponseDecisionType.LOCAL_AI_REQUIRED || decision.type == ResponseDecisionType.FALLBACK_REQUIRED) {
            providerRegistry.allProviders.filter { it.priority >= 3 }.sortedBy { it.priority }
        } else if (decision.selectedProvider != null) {
            listOf(decision.selectedProvider) + providerRegistry.allProviders.filter { it != decision.selectedProvider }.sortedBy { it.priority }
        } else {
            providerRegistry.allProviders.sortedBy { it.priority }
        }

        for (provider in providers) {
            if (provider.isAvailable()) {
                var attempt = 0
                var currentPrompt = memoryPrompt
                var candidateResult: AIResult.Success? = null

                while (attempt < 3) {
                    val result = provider.generateResponse(currentPrompt, rollingHistory, userInput)
                    if (result is AIResult.Success) {
                        val cleaned = responseQualityGuard.cleanPrefixes(result.text, activeCharacterId)
                        val check = responseQualityGuard.validate(cleaned, userInput, rollingHistory, characterName = activeCharacterId)
                        if (check is QualityCheckResult.Valid) {
                            candidateResult = AIResult.Success(
                                text = cleaned,
                                latencyMs = result.metadata.latencyMs,
                                metadata = result.metadata
                            )
                            break
                        } else if (check is QualityCheckResult.Invalid && provider !is FallbackAIProvider) {
                            attempt++
                            currentPrompt = ConversationPromptBuilder.buildCorrectiveRegenerationPrompt(
                                basePrompt = memoryPrompt,
                                failureReason = check.reason,
                                correctiveGuidance = check.correctivePrompt
                            )
                        } else {
                            // If fallback provider, accept response without looping
                            candidateResult = AIResult.Success(
                                text = cleaned.ifBlank { result.text },
                                latencyMs = result.metadata.latencyMs,
                                metadata = result.metadata
                            )
                            break
                        }
                    } else {
                        break
                    }
                }

                if (candidateResult != null) {
                    val finalText = candidateResult.text
                    RepetitionGuard.record(finalText)
                    cacheKnowledgeIfEligible(userInput, finalText, rollingHistory, decision.isSensitive)
                    val inTokens = ContextManager.estimateTokenCount(memoryPrompt) + ContextManager.estimateTokenCount(userInput)
                    val outTokens = ContextManager.estimateTokenCount(finalText)
                    usageTracker.recordTurn(provider.providerId, inTokens, outTokens, candidateResult.metadata.latencyMs, isCacheHit = false)
                    memoryManager.addTurn("user", userInput)
                    memoryManager.addTurn("assistant", finalText)
                    return candidateResult
                }
            }
        }

        // Final last-resort fallback
        val lastResort = fallbackAIEngine.generateResponse(systemPrompt, rollingHistory, userInput)
        if (lastResort is AIResult.Success) {
            memoryManager.addTurn("user", userInput)
            memoryManager.addTurn("assistant", lastResort.text)
        }
        return lastResort
    }

    override fun streamResponse(
        systemPrompt: String,
        conversationHistory: List<AITurn>,
        userInput: String
    ): Flow<String> = flow {
        val memoryPrompt = memoryManager.buildMemoryPrompt(systemPrompt)
        val rollingHistory = memoryManager.getRollingTurns().ifEmpty {
            ContextManager.compactHistory(conversationHistory)
        }

        // Level 0: Smart Token-Saving Decision
        val decision = decisionEngine.evaluateDecision(userInput, activeCharacterId, activeScenarioId)
        if (decision.type == ResponseDecisionType.MEMORY_ONLY ||
            decision.type == ResponseDecisionType.CACHE_EXACT ||
            decision.type == ResponseDecisionType.CACHE_SEMANTIC
        ) {
            val direct = decision.directResponse ?: "Here is what I remember."
            val tokensSaved = decision.estimatedTokensAvoided
            usageTracker.recordTurn("cache_memory_saver", 10, tokensSaved, 5, isCacheHit = true)
            memoryManager.addTurn("user", userInput)
            memoryManager.addTurn("assistant", direct)
            for (word in direct.split(" ")) {
                emit("$word ")
            }
            return@flow
        }

        // Multi-Provider Streaming Cascade
        val streamingProviders = if (usageTracker.isFairUseExceeded() || decision.type == ResponseDecisionType.LOCAL_AI_REQUIRED || decision.type == ResponseDecisionType.FALLBACK_REQUIRED) {
            providerRegistry.allProviders.filter { it.priority >= 3 }.sortedBy { it.priority }
        } else if (decision.selectedProvider != null) {
            listOf(decision.selectedProvider) + providerRegistry.allProviders.filter { it != decision.selectedProvider }.sortedBy { it.priority }
        } else {
            providerRegistry.allProviders.sortedBy { it.priority }
        }

        for (provider in streamingProviders) {
            if (provider.isAvailable()) {
                var anyToken = false
                val collected = StringBuilder()
                provider.streamResponse(memoryPrompt, rollingHistory, userInput).collect { token ->
                    if (token.isNotBlank()) anyToken = true
                    collected.append(token)
                    emit(token)
                }
                val full = collected.toString().trim()
                if (anyToken && full.isNotBlank()) {
                    val cleaned = responseQualityGuard.cleanPrefixes(full, activeCharacterId)
                    RepetitionGuard.record(cleaned)
                    cacheKnowledgeIfEligible(userInput, cleaned, rollingHistory, decision.isSensitive)
                    memoryManager.addTurn("user", userInput)
                    memoryManager.addTurn("assistant", cleaned)
                    return@flow
                }
            }
        }

        // Fallback streaming if all providers failed quality check
        val lastResort = fallbackAIEngine.generateResponse(systemPrompt, rollingHistory, userInput)
        if (lastResort is AIResult.Success) {
            memoryManager.addTurn("user", userInput)
            memoryManager.addTurn("assistant", lastResort.text)
            for (word in lastResort.text.split(" ")) {
                emit("$word ")
            }
        }
    }

    private fun lastAssistantTurns(history: List<AITurn>): List<String> {
        return history.filter { it.role == AITurn.Role.ASSISTANT }.takeLast(3).map { it.content }
    }

    private suspend fun cacheKnowledgeIfEligible(
        userInput: String,
        responseText: String,
        history: List<AITurn>,
        isSensitive: Boolean
    ) {
        if (isSensitive) return
        val category = determineCacheCategory(userInput, history)
        if (category == CacheCategory.DO_NOT_CACHE) return

        aiResponseCache.putResponse(
            category = category,
            characterId = activeCharacterId,
            scenarioId = activeScenarioId,
            userInput = userInput,
            responseText = responseText,
            contextHash = "global_knowledge",
            promptVersion = promptVersion
        )
    }

    private fun determineCacheCategory(userInput: String, history: List<AITurn>): CacheCategory {
        val lower = userInput.lowercase().trim()
        val isDefinitional = lower.contains("what does") && lower.contains("mean") ||
                lower.contains("how do you say") ||
                lower.contains("grammar rule for") ||
                lower.contains("vowels in english")
        return when {
            isDefinitional -> CacheCategory.KNOWLEDGE_CACHE
            else -> CacheCategory.DO_NOT_CACHE
        }
    }

    fun release() {
        memoryManager.clear()
        RepetitionGuard.reset()
    }
}

class LegacyCloudAIAdapter(private val cloudAIProvider: CloudAIProvider) : com.vaniflow.app.engine.ai.provider.AIProvider {
    override val providerId: String = "optional_cloud"
    override val providerName: String = "Optional Cloud Adapter"
    override val priority: Int = 2
    override var config: com.vaniflow.app.engine.ai.provider.ProviderConfig = com.vaniflow.app.engine.ai.provider.ProviderConfig(
        providerId = "optional_cloud",
        providerName = "Optional Cloud Adapter",
        priority = 2
    )
    override fun isAvailable(): Boolean = cloudAIProvider.isAvailable()
    override fun getHealthState(): com.vaniflow.app.engine.ai.provider.ProviderHealthState =
        if (cloudAIProvider.isAvailable()) com.vaniflow.app.engine.ai.provider.ProviderHealthState.AVAILABLE else com.vaniflow.app.engine.ai.provider.ProviderHealthState.OFFLINE
    override suspend fun generateResponse(systemPrompt: String, conversationHistory: List<AITurn>, userInput: String): AIResult =
        cloudAIProvider.generateResponse(systemPrompt, conversationHistory, userInput)
    override fun streamResponse(systemPrompt: String, conversationHistory: List<AITurn>, userInput: String): Flow<String> =
        cloudAIProvider.streamResponse(systemPrompt, conversationHistory, userInput)
    override fun recordSuccess(latencyMs: Long, tokensGenerated: Int) = cloudAIProvider.recordSuccess()
    override fun recordFailure(isRateLimit: Boolean) = cloudAIProvider.recordFailure(isRateLimit)
}