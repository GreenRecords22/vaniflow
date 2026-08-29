package com.vaniflow.app.engine.ai

import com.vaniflow.app.domain.model.ModelState
import com.vaniflow.app.engine.ai.llm.LocalLLMRuntime
import com.vaniflow.app.engine.ai.llm.LlmInferenceRequest
import com.vaniflow.app.engine.ai.llm.StubLocalLLMRuntime
import com.vaniflow.app.engine.model.ModelManager
import com.vaniflow.app.engine.model.VaniFlowModelTier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * On-Device VaniFlow Local AI Engine (Level 1 — PRIMARY conversational engine).
 *
 * Delegates genuine generation to a real [LocalLLMRuntime] ([LlamaCppRuntime],
 * backed by llama.cpp + a GGUF model). When the model file is present and loaded,
 * [isModelReady] returns true and this engine returns authentic, dynamic,
 * context-aware responses. It never fakes a response.
 */
@Singleton
open class LocalAIEngine @Inject constructor(
    private val modelManager: ModelManager,
    private val runtime: LocalLLMRuntime
) : AIEngine {

    private val defaultLlmId = "llm_qwen25_05b_instruct"

    /** Backward-compatible constructor for tests (uses the stub runtime). */
    constructor(modelManager: ModelManager) : this(modelManager, StubLocalLLMRuntime())

    private var activeCharacterId: String = "default_partner"
    private var activeScenarioId: String = "general"
    private var activeModelTier: String = VaniFlowModelTier.CORE.name

    override fun setActiveContext(characterId: String, scenarioId: String) {
        activeCharacterId = characterId
        activeScenarioId = scenarioId
    }

    fun setActiveModelTier(tier: VaniFlowModelTier) {
        activeModelTier = tier.name
    }

    /** True only when a real model file exists AND the runtime is loaded. */
    open fun isModelReady(): Boolean {
        val fileOk = runCatching {
            val file = modelManager.getModelFile(defaultLlmId)
            file != null && file.exists() && file.length() > 1_000_000L
        }.getOrDefault(false)
        return fileOk && runtime.isAvailable()
    }

    override suspend fun generateResponse(
        systemPrompt: String,
        conversationHistory: List<AITurn>,
        userInput: String
    ): AIResult = withContext(Dispatchers.Default) {
        val startTime = System.currentTimeMillis()

        if (!isModelReady()) {
            return@withContext AIResult.Error(
                message = "VaniFlow Local AI model is not loaded on this device.",
                isRecoverable = true
            )
        }

        val request = LlmInferenceRequest(
            systemPrompt = systemPrompt,
            history = conversationHistory,
            userInput = userInput,
            characterId = activeCharacterId,
            scenarioId = activeScenarioId,
            modelTier = activeModelTier
        )

        val responseText = runtime.generate(request)
            ?: return@withContext AIResult.Error("Local AI generation failed.", isRecoverable = true)

        val latency = System.currentTimeMillis() - startTime
        AIResult.Success(
            text = responseText,
            latencyMs = latency,
            metadata = AIResponseMetadata(
                routingLevel = AIRoutingLevel.LOCAL_SLM,
                latencyMs = latency,
                tokensGenerated = ContextManager.estimateTokenCount(responseText),
                providerName = "VaniFlow Local AI"
            )
        )
    }

    override fun streamResponse(
        systemPrompt: String,
        conversationHistory: List<AITurn>,
        userInput: String
    ): Flow<String> {
        if (!isModelReady()) return emptyFlow()

        val request = LlmInferenceRequest(
            systemPrompt = systemPrompt,
            history = conversationHistory,
            userInput = userInput,
            characterId = activeCharacterId,
            scenarioId = activeScenarioId,
            modelTier = activeModelTier
        )

        return runtime.stream(request).flowOn(Dispatchers.Default)
    }
}
