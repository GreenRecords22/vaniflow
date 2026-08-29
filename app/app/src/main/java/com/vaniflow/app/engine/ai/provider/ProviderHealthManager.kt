package com.vaniflow.app.engine.ai.provider

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Monitors provider health, failure counts, latency, and circuit-breaker state.
 */
@Singleton
class ProviderHealthManager @Inject constructor(
    private val quotaManager: ProviderQuotaManager
) {
    private val failureCounts = ConcurrentHashMap<String, AtomicInteger>()
    private val cooldownTimestamps = ConcurrentHashMap<String, AtomicLong>()
    private val averageLatencies = ConcurrentHashMap<String, AtomicLong>()

    fun evaluateHealth(provider: AIProvider): ProviderHealthState {
        val config = provider.config
        if (!config.isEnabled) return ProviderHealthState.OFFLINE

        val cooldownUntil = cooldownTimestamps[config.providerId]?.get() ?: 0L
        if (System.currentTimeMillis() < cooldownUntil) {
            return ProviderHealthState.ERROR
        }

        val failures = failureCounts[config.providerId]?.get() ?: 0
        if (failures >= config.maxConsecutiveErrors) {
            return ProviderHealthState.ERROR
        }

        if (quotaManager.isQuotaExhausted(config)) {
            return ProviderHealthState.QUOTA_EXHAUSTED
        }

        if (quotaManager.isQuotaWarning(config)) {
            return ProviderHealthState.QUOTA_WARNING
        }

        val avgLat = averageLatencies[config.providerId]?.get() ?: 0L
        if (avgLat > config.timeoutMs * 0.75) {
            return ProviderHealthState.DEGRADED
        }

        return ProviderHealthState.AVAILABLE
    }

    fun recordSuccess(providerId: String, latencyMs: Long, tokens: Int) {
        failureCounts[providerId]?.set(0)
        averageLatencies.compute(providerId) { _, current ->
            val prev = current?.get() ?: latencyMs
            AtomicLong((prev * 4 + latencyMs) / 5)
        }
        quotaManager.recordUsage(providerId, tokens)
    }

    fun recordFailure(provider: AIProvider, isRateLimit: Boolean = false) {
        val config = provider.config
        val count = failureCounts.computeIfAbsent(config.providerId) { AtomicInteger(0) }.incrementAndGet()
        if (isRateLimit || count >= config.maxConsecutiveErrors) {
            cooldownTimestamps.computeIfAbsent(config.providerId) { AtomicLong(0L) }
                .set(System.currentTimeMillis() + config.cooldownPeriodMs)
        }
    }

    fun isAvailable(provider: AIProvider): Boolean {
        val health = evaluateHealth(provider)
        return health == ProviderHealthState.AVAILABLE || health == ProviderHealthState.DEGRADED || health == ProviderHealthState.QUOTA_WARNING
    }

    fun reset(providerId: String) {
        failureCounts.remove(providerId)
        cooldownTimestamps.remove(providerId)
        averageLatencies.remove(providerId)
    }
}