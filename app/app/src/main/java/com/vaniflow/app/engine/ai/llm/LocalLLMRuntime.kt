package com.vaniflow.app.engine.ai.llm

import com.vaniflow.app.engine.ai.AITurn
import kotlinx.coroutines.flow.Flow

/**
 * Structured request handed to a local inference runtime.
 *
 * This is the single contract a real on-device language model must satisfy.
 * The production app ships [LlamaCppRuntime] (llama.cpp + GGUF) as the real
 * implementation, wired via Hilt in [AppModule]. It performs genuine token
 * generation. Do NOT replace it with a stub or another keyword matrix.
 */
data class LlmInferenceRequest(
    val systemPrompt: String,
    val history: List<AITurn>,
    val userInput: String,
    val characterId: String,
    val scenarioId: String,
    val modelTier: String
)

/**
 * Abstraction over a real on-device Small Language Model runtime.
 *
 * Implementations must perform genuine token generation from the supplied
 * prompt + history. They must NEVER return a hardcoded/canned sentence.
 *
 * Integration point (RESOLVED in M17):
 *  - `LlamaCppRuntime` loads a GGUF model via JNI (llama.cpp) and streams tokens.
 *  - An `OnnxLocalLLMRuntime` could run a quantized ONNX model as an alternative.
 * [StubLocalLLMRuntime] is kept only for tests; it reports `isAvailable() == false`.
 */
interface LocalLLMRuntime {
    /** True only when a real backend is loaded AND a real model file is present. */
    fun isAvailable(): Boolean

    /** Synchronous generation. Returns null if unavailable or on failure. */
    fun generate(request: LlmInferenceRequest): String?

    /** Streaming generation. Emits nothing if unavailable. */
    fun stream(request: LlmInferenceRequest): Flow<String>

    /** Release native/model resources safely. */
    fun release()
}
