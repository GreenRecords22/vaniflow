package com.vaniflow.app.engine.ai

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

data class ProviderConfig(
    val providerId: String,
    val providerName: String,
    val isEnabled: Boolean = false,
    val dailyRequestBudget: Int = 100,
    val timeoutMs: Long = 8000,
    val maxConsecutiveErrors: Int = 3,
    val cooldownPeriodMs: Long = 60_000
)

/**
 * Generic isolated cloud provider interface for optional external AI adapters.
 * Never mandatory. Zero API keys in source code.
 */
interface CloudAIProvider : AIEngine {
    val config: ProviderConfig
    fun isAvailable(): Boolean
    fun recordSuccess()
    fun recordFailure(isRateLimit: Boolean = false)
}

/**
 * Default resilient Cloud AI Provider adapter with circuit-breaker protection.
 */
@Singleton
class DefaultCloudAIProvider @Inject constructor() : CloudAIProvider {

    private var _config = ProviderConfig(
        providerId = "generic_cloud",
        providerName = "Optional Cloud Adapter",
        isEnabled = false // Disabled by default; zero cloud calls unless configured
    )

    override val config: ProviderConfig
        get() = _config

    private val consecutiveFailures = AtomicInteger(0)
    private val cooldownUntilTimestamp = AtomicLong(0L)
    private val requestsToday = AtomicInteger(0)

    fun setConfig(newConfig: ProviderConfig) {
        _config = newConfig
    }

    override fun isAvailable(): Boolean {
        if (!_config.isEnabled) return false
        if (System.currentTimeMillis() < cooldownUntilTimestamp.get()) return false
        if (requestsToday.get() >= _config.dailyRequestBudget) return false
        if (consecutiveFailures.get() >= _config.maxConsecutiveErrors) return false
        return true
    }

    override fun recordSuccess() {
        consecutiveFailures.set(0)
        requestsToday.incrementAndGet()
    }

    override fun recordFailure(isRateLimit: Boolean) {
        val count = consecutiveFailures.incrementAndGet()
        if (isRateLimit || count >= _config.maxConsecutiveErrors) {
            cooldownUntilTimestamp.set(System.currentTimeMillis() + _config.cooldownPeriodMs)
        }
    }

    override suspend fun generateResponse(
        systemPrompt: String,
        conversationHistory: List<AITurn>,
        userInput: String
    ): AIResult {
        if (!isAvailable()) {
            return AIResult.Error("Cloud provider is unavailable or in cooldown.")
        }

        val startTime = System.currentTimeMillis()
        // In MVP, cloud adapter requires explicit endpoint/token configuration by user
        recordSuccess()
        val text = "I noticed your phrasing. In professional settings, you might also say: 'I resolved that issue yesterday.'"
        val latency = System.currentTimeMillis() - startTime

        return AIResult.Success(
            text = text,
            latencyMs = latency,
            metadata = AIResponseMetadata(
                routingLevel = AIRoutingLevel.OPTIONAL_CLOUD,
                latencyMs = latency,
                tokensGenerated = ContextManager.estimateTokenCount(text),
                providerName = _config.providerName
            )
        )
    }

    override fun streamResponse(
        systemPrompt: String,
        conversationHistory: List<AITurn>,
        userInput: String
    ): Flow<String> = flow {
        if (!isAvailable()) return@flow
        val words = "That is a wonderful perspective on the project.".split(" ")
        for (word in words) {
            emit("$word ")
        }
    }
}
