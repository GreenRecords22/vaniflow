package com.vaniflow.app.engine.ai.provider

import com.vaniflow.app.engine.ai.AIResult
import com.vaniflow.app.engine.ai.AITurn
import com.vaniflow.app.engine.ai.ContextAwareFallbackEngine
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Context-Aware Fallback Provider (Priority 4).
 * Ensures a natural conversational experience even when no AI model is active.
 */
@Singleton
class FallbackAIProvider @Inject constructor(
    private val fallbackEngine: ContextAwareFallbackEngine
) : AIProvider {

    override val providerId: String = "fallback_conversational"
    override val providerName: String = "VaniFlow Conversational Safety Net"
    override val priority: Int = 4

    override val config: ProviderConfig = ProviderConfig(
        providerId = providerId,
        providerName = providerName,
        isEnabled = true,
        priority = 4
    )

    override fun isAvailable(): Boolean = true

    override fun getHealthState(): ProviderHealthState = ProviderHealthState.AVAILABLE

    override suspend fun generateResponse(
        systemPrompt: String,
        conversationHistory: List<AITurn>,
        userInput: String
    ): AIResult = fallbackEngine.generateResponse(systemPrompt, conversationHistory, userInput)

    override fun streamResponse(
        systemPrompt: String,
        conversationHistory: List<AITurn>,
        userInput: String
    ): Flow<String> = fallbackEngine.streamResponse(systemPrompt, conversationHistory, userInput)

    override fun recordSuccess(latencyMs: Long, tokensGenerated: Int) {}
    override fun recordFailure(isRateLimit: Boolean) {}
}