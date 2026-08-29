package com.vaniflow.app.engine.ai.provider

import com.vaniflow.app.engine.ai.AIResponseMetadata
import com.vaniflow.app.engine.ai.AIResult
import com.vaniflow.app.engine.ai.AIRoutingLevel
import com.vaniflow.app.engine.ai.AITurn
import com.vaniflow.app.engine.ai.ContextManager
import com.vaniflow.app.engine.ai.LocalAIEngine
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Local On-Device VaniFlow AI Provider (Priority 3).
 * Offline fallback when internet is disabled or API quotas are exhausted.
 */
@Singleton
class LocalAIProvider @Inject constructor(
    private val localAIEngine: LocalAIEngine,
    private val healthManager: ProviderHealthManager
) : AIProvider {

    override val providerId: String = "local_vaniflow"
    override val providerName: String = "VaniFlow On-Device"
    override val priority: Int = 3

    override val config: ProviderConfig = ProviderConfig(
        providerId = providerId,
        providerName = providerName,
        rpm = 0, // Unmetered on-device
        rpd = 0,
        tpm = 0,
        tpd = 0,
        isEnabled = true,
        priority = 3,
        timeoutMs = 15_000L
    )

    override fun isAvailable(): Boolean {
        return localAIEngine.isModelReady() && healthManager.isAvailable(this)
    }

    override fun getHealthState(): ProviderHealthState {
        return if (localAIEngine.isModelReady()) ProviderHealthState.AVAILABLE else ProviderHealthState.OFFLINE
    }

    override suspend fun generateResponse(
        systemPrompt: String,
        conversationHistory: List<AITurn>,
        userInput: String
    ): AIResult {
        if (!isAvailable()) {
            recordFailure()
            return AIResult.Error("Local VaniFlow model is not installed or ready.")
        }
        val startTime = System.currentTimeMillis()
        val result = localAIEngine.generateResponse(systemPrompt, conversationHistory, userInput)
        val latency = System.currentTimeMillis() - startTime
        if (result is AIResult.Success) {
            recordSuccess(latency, result.metadata.tokensGenerated)
        } else {
            recordFailure()
        }
        return result
    }

    override fun streamResponse(
        systemPrompt: String,
        conversationHistory: List<AITurn>,
        userInput: String
    ): Flow<String> {
        return localAIEngine.streamResponse(systemPrompt, conversationHistory, userInput)
    }

    override fun recordSuccess(latencyMs: Long, tokensGenerated: Int) {
        healthManager.recordSuccess(providerId, latencyMs, tokensGenerated)
    }

    override fun recordFailure(isRateLimit: Boolean) {
        healthManager.recordFailure(this, isRateLimit)
    }
}