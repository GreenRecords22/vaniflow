package com.vaniflow.app.engine.ai.llm

import kotlinx.coroutines.flow.flow

/**
 * Test double for [LocalLLMRuntime]. Returns dynamic, input-aware text so tests
 * can verify genuine (non-canned) generation and routing without a real model.
 */
class FakeLocalLLMRuntime(
    private val available: Boolean = true,
    private val respond: (LlmInferenceRequest) -> String = { req ->
        "You mentioned: \"${req.userInput}\". Tell me more about that."
    }
) : LocalLLMRuntime {

    override fun isAvailable(): Boolean = available

    override fun generate(request: LlmInferenceRequest): String? =
        if (available) respond(request) else null

    override fun stream(request: LlmInferenceRequest) = flow {
        if (!available) return@flow
        val text = respond(request)
        text.split(" ").forEach { emit("$it ") }
    }

    override fun release() = Unit
}
