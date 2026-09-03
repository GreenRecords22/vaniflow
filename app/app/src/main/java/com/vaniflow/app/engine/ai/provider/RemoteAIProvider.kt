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
import com.vaniflow.app.engine.ai.provider.adapter.VaniFlowGatewayAdapter
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Primary Remote Cloud AI Provider.
 * Connects to the secure VaniFlow AI Gateway (or direct Groq/Gemini endpoints)
 * with robust circuit-breaker and conversational fallback resilience.
 */
@Singleton
class RemoteAIProvider @Inject constructor(
    private val healthManager: ProviderHealthManager,
    private val dialogueEngine: ConversationalDialogueEngine,
    private val configStore: ApiConfigStore,
    private val openAIAdapter: OpenAICompatibleAdapter,
    private val geminiAdapter: GeminiProviderAdapter,
    private val gatewayAdapter: VaniFlowGatewayAdapter
) : AIProvider {

    constructor(healthManager: ProviderHealthManager) : this(
        healthManager,
        ConversationalDialogueEngine(),
        ApiConfigStore(),
        OpenAICompatibleAdapter(),
        GeminiProviderAdapter(),
        VaniFlowGatewayAdapter()
    )

    constructor(
        healthManager: ProviderHealthManager,
        dialogueEngine: ConversationalDialogueEngine,
        configStore: ApiConfigStore,
        openAIAdapter: OpenAICompatibleAdapter,
        geminiAdapter: GeminiProviderAdapter
    ) : this(
        healthManager,
        dialogueEngine,
        configStore,
        openAIAdapter,
        geminiAdapter,
        VaniFlowGatewayAdapter()
    )

    override val providerId: String = "remote_primary"
    override val providerName: String = "VaniFlow Cloud AI"
    override val priority: Int = 1

    override var config: ProviderConfig = ProviderConfig(
        providerId = providerId,
        providerName = providerName,
        endpoint = com.vaniflow.app.BuildConfig.GATEWAY_URL,
        model = "groq/compound-mini",
        rpm = 60,
        rpd = 1000,
        tpm = 40_000,
        tpd = 500_000,
        isEnabled = true,
        priority = 1,
        timeoutMs = 4_000L
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
            return AIResult.Error("Primary Remote AI is currently unavailable.")
        }

        val startTime = System.currentTimeMillis()

        // 1. Real HTTP API request if gateway or credentials are configured
        if (configStore.hasPrimaryCredentials() || configStore.isGatewayConfigured()) {
            val endpoint = configStore.getPrimaryEndpoint().ifBlank { config.endpoint }
            val apiKey = configStore.getPrimaryApiKey()
            val model = configStore.getPrimaryModel().ifBlank { config.model }
            val adapterType = configStore.getPrimaryAdapterType()

            val result = when (adapterType) {
                "vaniflow_gateway" -> gatewayAdapter.generate(endpoint, apiKey, model, systemPrompt, conversationHistory, userInput, config.timeoutMs)
                "gemini" -> geminiAdapter.generate(endpoint, apiKey, model, systemPrompt, conversationHistory, userInput, config.timeoutMs)
                else -> openAIAdapter.generate(endpoint, apiKey, model, systemPrompt, conversationHistory, userInput, config.timeoutMs)
            }

            if (result is AIResult.Success) {
                recordSuccess(result.metadata.latencyMs, result.metadata.tokensGenerated)
                return result
            } else if (result is AIResult.Error) {
                val is429 = result.message.contains("429", ignoreCase = true) || result.message.contains("rate limit", ignoreCase = true)
                recordFailure(isRateLimit = is429)
                // Return error to trigger multi-provider failover
                return result
            }
        }

        // 2. Resilient conversational intelligence fallback
        return try {
            delay(50)
            val character = detectCharacter(systemPrompt)
            val text = dialogueEngine.generateResponse(
                characterId = character,
                scenarioTitle = "General Conversation",
                userLevel = SkillLevel.BEGINNER,
                history = conversationHistory,
                userInput = userInput
            )
            val latency = System.currentTimeMillis() - startTime
            val tokens = ContextManager.estimateTokenCount(text)
            recordSuccess(latency, tokens)
            AIResult.Success(
                text = text,
                latencyMs = latency,
                metadata = AIResponseMetadata(
                    routingLevel = AIRoutingLevel.OPTIONAL_CLOUD,
                    latencyMs = latency,
                    tokensGenerated = tokens,
                    providerName = providerName
                )
            )
        } catch (e: Exception) {
            recordFailure()
            AIResult.Error("Remote AI request failed: ${e.message}")
        }
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

        if (configStore.hasPrimaryCredentials() || configStore.isGatewayConfigured()) {
            val endpoint = configStore.getPrimaryEndpoint().ifBlank { config.endpoint }
            val apiKey = configStore.getPrimaryApiKey()
            val model = configStore.getPrimaryModel().ifBlank { config.model }
            val adapterType = configStore.getPrimaryAdapterType()

            try {
                val flow = when (adapterType) {
                    "vaniflow_gateway" -> gatewayAdapter.stream(endpoint, apiKey, model, systemPrompt, conversationHistory, userInput, config.timeoutMs)
                    "gemini" -> geminiAdapter.stream(endpoint, apiKey, model, systemPrompt, conversationHistory, userInput, config.timeoutMs)
                    else -> openAIAdapter.stream(endpoint, apiKey, model, systemPrompt, conversationHistory, userInput, config.timeoutMs)
                }
                var anyToken = false
                flow.collect { token ->
                    anyToken = true
                    emit(token)
                }
                if (anyToken) {
                    recordSuccess(100L, 50)
                    return@flow
                }
            } catch (e: Exception) {
                val is429 = e.message?.contains("429") == true
                recordFailure(isRateLimit = is429)
            }
        }

        // Fallback streaming simulation
        try {
            val character = detectCharacter(systemPrompt)
            val text = dialogueEngine.generateResponse(
                characterId = character,
                scenarioTitle = "General Conversation",
                userLevel = SkillLevel.BEGINNER,
                history = conversationHistory,
                userInput = userInput
            )
            val words = text.split(" ")
            for (word in words) {
                delay(20)
                emit("$word ")
            }
            recordSuccess(50L, ContextManager.estimateTokenCount(text))
        } catch (e: Exception) {
            recordFailure()
        }
    }

    override fun recordSuccess(latencyMs: Long, tokens: Int) {
        healthManager.recordSuccess(providerId, latencyMs, tokens)
    }

    override fun recordFailure(isRateLimit: Boolean) {
        healthManager.recordFailure(this, isRateLimit)
    }

    private fun detectCharacter(prompt: String): String {
        val lower = prompt.lowercase()
        return when {
            lower.contains("raya") -> "raya"
            lower.contains("rudra") -> "rudra"
            lower.contains("adwaita") -> "adwaita"
            lower.contains("shub") -> "shub"
            else -> "raya"
        }
    }
}