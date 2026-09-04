package com.vaniflow.app.engine.ai.provider

import com.vaniflow.app.domain.model.SkillLevel
import com.vaniflow.app.engine.ai.AIResponseMetadata
import com.vaniflow.app.engine.ai.AIResult
import com.vaniflow.app.engine.ai.AIRoutingLevel
import com.vaniflow.app.engine.ai.AITurn
import com.vaniflow.app.engine.ai.ContextManager
import com.vaniflow.app.engine.ai.ConversationalDialogueEngine
import com.vaniflow.app.engine.ai.provider.adapter.GeminiProviderAdapter
import com.vaniflow.app.engine.ai.provider.adapter.OpenAICompatibleAdapter
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Secondary Remote Cloud AI Provider (Failover Provider).
 * Automatically invoked when Primary Remote AI encounters 80% quota warning,
 * 429 rate limit, 5xx server error, or connection timeouts.
 */
@Singleton
class SecondaryRemoteAIProvider @Inject constructor(
    private val healthManager: ProviderHealthManager,
    private val dialogueEngine: ConversationalDialogueEngine,
    private val configStore: ApiConfigStore,
    private val openAIAdapter: OpenAICompatibleAdapter,
    private val geminiAdapter: GeminiProviderAdapter
) : AIProvider {

    constructor(healthManager: ProviderHealthManager) : this(
        healthManager,
        ConversationalDialogueEngine(),
        ApiConfigStore(),
        OpenAICompatibleAdapter(),
        GeminiProviderAdapter()
    )

    override val providerId: String = "remote_secondary"
    override val providerName: String = "VaniFlow Cloud Backup"
    override val priority: Int = 2

    override var config: ProviderConfig = ProviderConfig(
        providerId = providerId,
        providerName = providerName,
        endpoint = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent",
        model = "gemini-1.5-flash",
        rpm = 15,
        rpd = 1500,
        tpm = 1_000_000,
        tpd = 1_000_000,
        isEnabled = true,
        priority = 2,
        timeoutMs = 5_000L
    )

    override fun isAvailable(): Boolean = healthManager.isAvailable(this)

    override fun getHealthState(): ProviderHealthState = healthManager.evaluateHealth(this)

    override suspend fun generateResponse(
        systemPrompt: String,
        conversationHistory: List<AITurn>,
        userInput: String
    ): AIResult {
        if (!isAvailable()) {
            recordFailure()
            return AIResult.Error("Secondary Remote AI is currently unconfigured or unavailable.")
        }

        val hasCreds = configStore.hasSecondaryCredentials()
        if (hasCreds) {
            val endpoint = configStore.getSecondaryEndpoint().ifBlank { config.endpoint }
            val apiKey = configStore.getSecondaryApiKey()
            val model = configStore.getSecondaryModel().ifBlank { config.model }
            val adapterType = configStore.getSecondaryAdapterType()

            val result = if (adapterType == "gemini") {
                geminiAdapter.generate(endpoint, apiKey, model, systemPrompt, conversationHistory, userInput, config.timeoutMs)
            } else {
                openAIAdapter.generate(endpoint, apiKey, model, systemPrompt, conversationHistory, userInput, config.timeoutMs)
            }

            if (result is AIResult.Success) {
                recordSuccess(result.metadata.latencyMs, result.metadata.tokensGenerated)
                return result
            } else if (result is AIResult.Error) {
                val is429 = result.message.contains("429", ignoreCase = true) || result.message.contains("rate limit", ignoreCase = true)
                recordFailure(isRateLimit = is429)
                return result
            }
        }

        // Offline / unconfigured test fallback
        val charId = detectCharacter(systemPrompt)
        val text = dialogueEngine.generateResponse(charId, "", SkillLevel.INTERMEDIATE, conversationHistory, userInput)
        val latency = 50L
        recordSuccess(latency, 20)
        return AIResult.Success(
            text = text,
            latencyMs = latency,
            metadata = AIResponseMetadata(
                routingLevel = AIRoutingLevel.OPTIONAL_CLOUD,
                latencyMs = latency,
                tokensGenerated = 20,
                providerName = providerName
            )
        )
    }

    override fun streamResponse(
        systemPrompt: String,
        conversationHistory: List<AITurn>,
        userInput: String
    ): Flow<String> = flow {
        if (!isAvailable()) {
            recordFailure()
            return@flow
        }

        val hasCreds = configStore.hasSecondaryCredentials()
        if (hasCreds) {
            val endpoint = configStore.getSecondaryEndpoint().ifBlank { config.endpoint }
            val apiKey = configStore.getSecondaryApiKey()
            val model = configStore.getSecondaryModel().ifBlank { config.model }
            val adapterType = configStore.getSecondaryAdapterType()

            try {
                val flow = if (adapterType == "gemini") {
                    geminiAdapter.stream(endpoint, apiKey, model, systemPrompt, conversationHistory, userInput, config.timeoutMs)
                } else {
                    openAIAdapter.stream(endpoint, apiKey, model, systemPrompt, conversationHistory, userInput, config.timeoutMs)
                }
                var anyToken = false
                flow.collect { token ->
                    anyToken = true
                    emit(token)
                }
                if (anyToken) {
                    recordSuccess(100L, 50)
                }
            } catch (e: Exception) {
                val is429 = e.message?.contains("429") == true
                recordFailure(isRateLimit = is429)
                throw e
            }
        } else {
            val charId = detectCharacter(systemPrompt)
            val text = dialogueEngine.generateResponse(charId, "", SkillLevel.INTERMEDIATE, conversationHistory, userInput)
            val words = text.split(" ")
            for (word in words) {
                emit("$word ")
                delay(20L)
            }
            recordSuccess(50L, words.size)
        }
    }

    private fun detectCharacter(systemPrompt: String): String {
        val lower = systemPrompt.lowercase()
        return when {
            lower.contains("rudra") -> "rudra"
            lower.contains("adwaita") -> "adwaita"
            lower.contains("shub") -> "shub"
            else -> "raya"
        }
    }

    override fun recordSuccess(latencyMs: Long, tokensGenerated: Int) {
        healthManager.recordSuccess(providerId, latencyMs, tokensGenerated)
    }

    override fun recordFailure(isRateLimit: Boolean) {
        healthManager.recordFailure(this, isRateLimit)
    }
}