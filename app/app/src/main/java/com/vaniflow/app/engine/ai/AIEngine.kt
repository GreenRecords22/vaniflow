package com.vaniflow.app.engine.ai

import kotlinx.coroutines.flow.Flow

/**
 * Routing levels managed by SmartAIRouter.
 */
enum class AIRoutingLevel {
    DETERMINISTIC_RULE,     // Level 0: Direct rule / dictionary / grammar lookup
    LOCAL_SLM,              // Level 1: On-device Small Language Model (VaniFlow Local AI, PRIMARY)
    OPTIONAL_CLOUD,         // Level 2: Optional user-configured cloud provider
    CONTEXT_AWARE_FALLBACK, // Level 3: Graceful context-aware degradation
    SCENARIO_MATRIX,        // Level 4: Deterministic scenario matrix (legacy fallback)
    LOCAL_FALLBACK,         // Legacy alias for SCENARIO_MATRIX
    EMERGENCY_FALLBACK      // Level 5: Minimal emergency safe fallback
}

/**
 * Telemetry and diagnostics metadata for AI turn generation.
 */
data class AIResponseMetadata(
    val routingLevel: AIRoutingLevel,
    val latencyMs: Long = 0,
    val tokensGenerated: Int = 0,
    val providerName: String = "Local Engine",
    val fallbackReason: String? = null
)

data class AITurn(
    val role: Role,
    val content: String
) {
    enum class Role {
        SYSTEM,
        USER,
        ASSISTANT
    }
}

sealed class AIResult {
    data class Success(
        val text: String,
        val latencyMs: Long = 0,
        val metadata: AIResponseMetadata = AIResponseMetadata(AIRoutingLevel.LOCAL_FALLBACK, latencyMs)
    ) : AIResult()

    data class Error(
        val message: String,
        val cause: Throwable? = null,
        val isRecoverable: Boolean = true
    ) : AIResult()
}

/**
 * Provider-agnostic AI Engine interface.
 * All conversational intelligence passes through this contract.
 */
interface AIEngine {
    suspend fun generateResponse(
        systemPrompt: String,
        conversationHistory: List<AITurn>,
        userInput: String
    ): AIResult

    fun streamResponse(
        systemPrompt: String,
        conversationHistory: List<AITurn>,
        userInput: String
    ): Flow<String>

    /**
     * Optional context hook so routers can scope caches/fallbacks to the active
     * character and scenario. Default no-op for engines that don't need it.
     */
    fun setActiveContext(characterId: String, scenarioId: String) {}
}
