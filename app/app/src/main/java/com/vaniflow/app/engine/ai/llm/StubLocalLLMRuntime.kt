package com.vaniflow.app.engine.ai.llm

import kotlinx.coroutines.flow.emptyFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Default [LocalLLMRuntime] shipped with the current build.
 *
 * It intentionally does NOT implement inference. No usable native inference
 * backend (llama.cpp / ONNX Runtime) and no real on-device model file are
 * present in this repository, so genuine local generation is **BLOCKED**.
 *
 * This stub exists so the rest of the architecture (router, prompt builder,
 * repetition guard, cache, avatar sync) is fully wired and production-shaped.
 * Replacing this stub with a real runtime that satisfies [LocalLLMRuntime]
 * is the single change required to enable authentic AI conversation — without
 * any modification to the rest of the codebase.
 */
@Singleton
class StubLocalLLMRuntime @Inject constructor() : LocalLLMRuntime {

    override fun isAvailable(): Boolean = false

    override fun generate(request: LlmInferenceRequest): String? = null

    override fun stream(request: LlmInferenceRequest) = emptyFlow<String>()

    override fun release() = Unit
}
