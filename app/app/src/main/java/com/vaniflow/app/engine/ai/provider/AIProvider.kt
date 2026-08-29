package com.vaniflow.app.engine.ai.provider

import com.vaniflow.app.engine.ai.AIResult
import com.vaniflow.app.engine.ai.AITurn
import kotlinx.coroutines.flow.Flow

/**
 * Health status of an AI provider.
 */
enum class ProviderHealthState {
    AVAILABLE,
    DEGRADED,
    QUOTA_WARNING,
    QUOTA_EXHAUSTED,
    OFFLINE,
    ERROR
}

/**
 * Configuration for an AI provider.
 */
data class ProviderConfig(
    val providerId: String,
    val providerName: String,
    val endpoint: String = "",
    val model: String = "",
    val rpm: Int = 60,
    val rpd: Int = 1000,
    val tpm: Int = 40_000,
    val tpd: Int = 500_000,
    val isEnabled: Boolean = true,
    val priority: Int = 1, // 1 = Primary, 2 = Secondary, 3 = Local Fallback, etc.
    val timeoutMs: Long = 5_000L,
    val maxConsecutiveErrors: Int = 3,
    val cooldownPeriodMs: Long = 60_000L
)

/**
 * Unified interface for all conversational AI providers (Remote, Local, and Fallback).
 */
interface AIProvider {
    val providerId: String
    val providerName: String
    val priority: Int
    val config: ProviderConfig

    fun isAvailable(): Boolean
    fun getHealthState(): ProviderHealthState

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

    fun recordSuccess(latencyMs: Long, tokensGenerated: Int)
    fun recordFailure(isRateLimit: Boolean = false)
}