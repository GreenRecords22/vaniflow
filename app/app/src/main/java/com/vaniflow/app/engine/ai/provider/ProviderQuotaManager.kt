package com.vaniflow.app.engine.ai.provider

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages request and token budgets per provider with 80% warning and auto-exhaustion switching.
 */
@Singleton
class ProviderQuotaManager @Inject constructor() {

    private val dailyRequests = ConcurrentHashMap<String, AtomicInteger>()
    private val dailyTokens = ConcurrentHashMap<String, AtomicLong>()
    private val minuteRequests = ConcurrentHashMap<String, AtomicInteger>()
    private val minuteTokens = ConcurrentHashMap<String, AtomicLong>()
    private var lastMinuteReset = System.currentTimeMillis()
    private var lastDayReset = System.currentTimeMillis()

    private fun checkResets() {
        val now = System.currentTimeMillis()
        if (now - lastMinuteReset > 60_000L) {
            minuteRequests.values.forEach { it.set(0) }
            minuteTokens.values.forEach { it.set(0) }
            lastMinuteReset = now
        }
        if (now - lastDayReset > 86_400_000L) {
            dailyRequests.values.forEach { it.set(0) }
            dailyTokens.values.forEach { it.set(0) }
            lastDayReset = now
        }
    }

    fun isQuotaWarning(config: ProviderConfig): Boolean {
        checkResets()
        val reqs = dailyRequests[config.providerId]?.get() ?: 0
        val tokens = dailyTokens[config.providerId]?.get() ?: 0L
        val reqWarning = config.rpd > 0 && reqs >= (config.rpd * 0.8)
        val tokenWarning = config.tpd > 0 && tokens >= (config.tpd * 0.8)
        return reqWarning || tokenWarning
    }

    fun isQuotaExhausted(config: ProviderConfig): Boolean {
        checkResets()
        val dReqs = dailyRequests[config.providerId]?.get() ?: 0
        val dTokens = dailyTokens[config.providerId]?.get() ?: 0L
        val mReqs = minuteRequests[config.providerId]?.get() ?: 0
        val mTokens = minuteTokens[config.providerId]?.get() ?: 0L

        if (config.rpd > 0 && dReqs >= config.rpd) return true
        if (config.tpd > 0 && dTokens >= config.tpd) return true
        if (config.rpm > 0 && mReqs >= config.rpm) return true
        if (config.tpm > 0 && mTokens >= config.tpm) return true
        return false
    }

    fun recordUsage(providerId: String, tokens: Int) {
        checkResets()
        dailyRequests.computeIfAbsent(providerId) { AtomicInteger(0) }.incrementAndGet()
        dailyTokens.computeIfAbsent(providerId) { AtomicLong(0L) }.addAndGet(tokens.toLong())
        minuteRequests.computeIfAbsent(providerId) { AtomicInteger(0) }.incrementAndGet()
        minuteTokens.computeIfAbsent(providerId) { AtomicLong(0L) }.addAndGet(tokens.toLong())
    }

    fun getUsage(providerId: String): Pair<Int, Long> {
        checkResets()
        val reqs = dailyRequests[providerId]?.get() ?: 0
        val tokens = dailyTokens[providerId]?.get() ?: 0L
        return Pair(reqs, tokens)
    }

    fun resetAll() {
        dailyRequests.clear()
        dailyTokens.clear()
        minuteRequests.clear()
        minuteTokens.clear()
    }
}