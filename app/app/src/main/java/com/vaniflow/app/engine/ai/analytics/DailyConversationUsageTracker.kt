package com.vaniflow.app.engine.ai.analytics

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tracks local daily conversation minutes, token consumption, cache savings, and provider failure telemetry.
 */
@Singleton
class DailyConversationUsageTracker @Inject constructor() {

    private val totalSpeakingSeconds = AtomicLong(0L)
    private val dailyMinutes = AtomicInteger(0)
    private val totalRequests = AtomicInteger(0)
    private val inputTokens = AtomicLong(0L)
    private val outputTokens = AtomicLong(0L)
    private val cacheHits = AtomicInteger(0)
    private val cacheMisses = AtomicInteger(0)
    private val savedTokens = AtomicLong(0L)
    private val providerUsage = ConcurrentHashMap<String, AtomicInteger>()
    private val providerFailures = ConcurrentHashMap<String, AtomicInteger>()
    private val latencies = ConcurrentHashMap<String, AtomicLong>()

    var fairUseMinutesTarget: Int = 90

    fun recordTurn(
        providerId: String,
        inputTokenCount: Int,
        outputTokenCount: Int,
        latencyMs: Long,
        isCacheHit: Boolean = false
    ) {
        totalRequests.incrementAndGet()
        providerUsage.computeIfAbsent(providerId) { AtomicInteger(0) }.incrementAndGet()

        if (isCacheHit) {
            cacheHits.incrementAndGet()
            savedTokens.addAndGet(outputTokenCount.toLong())
        } else {
            cacheMisses.incrementAndGet()
            inputTokens.addAndGet(inputTokenCount.toLong())
            outputTokens.addAndGet(outputTokenCount.toLong())
            latencies.compute(providerId) { _, current ->
                val prev = current?.get() ?: latencyMs
                AtomicLong((prev * 4 + latencyMs) / 5)
            }
        }
    }

    fun recordFailure(providerId: String) {
        providerFailures.computeIfAbsent(providerId) { AtomicInteger(0) }.incrementAndGet()
    }

    fun addSpeakingDurationSeconds(seconds: Int) {
        val totalSecs = totalSpeakingSeconds.addAndGet(seconds.toLong())
        dailyMinutes.set((totalSecs / 60).toInt())
    }

    fun isFairUseExceeded(): Boolean = (totalSpeakingSeconds.get() / 60) >= fairUseMinutesTarget

    fun getDailyMinutes(): Int = dailyMinutes.get()
    fun getTotalRequests(): Int = totalRequests.get()
    fun getInputTokens(): Long = inputTokens.get()
    fun getOutputTokens(): Long = outputTokens.get()
    fun getCacheHits(): Int = cacheHits.get()
    fun getSavedTokens(): Long = savedTokens.get()

    fun getTokensPerMinute(): Double {
        val mins = dailyMinutes.get()
        return if (mins > 0) (inputTokens.get() + outputTokens.get()).toDouble() / mins else 0.0
    }

    fun getRequestsPerMinute(): Double {
        val mins = dailyMinutes.get()
        return if (mins > 0) totalRequests.get().toDouble() / mins else 0.0
    }

    fun getCacheHitRate(): Double {
        val total = cacheHits.get() + cacheMisses.get()
        return if (total > 0) (cacheHits.get().toDouble() / total) * 100.0 else 0.0
    }

    fun getAverageResponseTokens(): Double {
        val reqs = totalRequests.get()
        return if (reqs > 0) outputTokens.get().toDouble() / reqs else 0.0
    }

    fun getTelemetrySummary(): Map<String, Any> {
        return mapOf(
            "dailyMinutes" to dailyMinutes.get(),
            "fairUseTarget" to fairUseMinutesTarget,
            "fairUseExceeded" to isFairUseExceeded(),
            "totalRequests" to totalRequests.get(),
            "inputTokens" to inputTokens.get(),
            "outputTokens" to outputTokens.get(),
            "cacheHits" to cacheHits.get(),
            "cacheMisses" to cacheMisses.get(),
            "savedTokens" to savedTokens.get(),
            "cacheHitRatePercent" to getCacheHitRate(),
            "tokensPerMinute" to getTokensPerMinute(),
            "providerUsage" to providerUsage.mapValues { it.value.get() },
            "providerFailures" to providerFailures.mapValues { it.value.get() }
        )
    }

    fun resetDaily() {
        dailyMinutes.set(0)
        totalRequests.set(0)
        inputTokens.set(0L)
        outputTokens.set(0L)
        cacheHits.set(0)
        cacheMisses.set(0)
        savedTokens.set(0L)
        providerUsage.clear()
        providerFailures.clear()
        latencies.clear()
    }
}