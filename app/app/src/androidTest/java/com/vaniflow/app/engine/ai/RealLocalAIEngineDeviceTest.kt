package com.vaniflow.app.engine.ai

import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import com.vaniflow.app.engine.ai.llm.LlamaCppRuntime
import com.vaniflow.app.engine.ai.llm.LocalLLMRuntime
import com.vaniflow.app.engine.model.DefaultModelManager
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Copies the real GGUF from world-readable /data/local/tmp into the app's own
 * files dir so the on-device test is reproducible on any attached device.
 */
private fun ensureModelOnDevice(ctx: android.content.Context) {
    val src = File("/data/local/tmp/llm_qwen25_05b.gguf")
    val dst = File(ctx.filesDir, "models/llm_qwen25_05b.gguf")
    if (dst.exists() && dst.length() == src.length()) return
    if (!src.exists()) return
    dst.parentFile?.mkdirs()
    src.copyTo(dst, overwrite = true)
}

/**
 * Full-stack on-device validation (Milestone 17, Phase 14 — LLM portion).
 *
 * Drives the real [LocalAIEngine] (SmartAIRouter L1 primary) which delegates to
 * [LlamaCppRuntime] -> llama.cpp JNI -> Qwen2.5-0.5B GGUF. Proves genuine token
 * streaming through the engine layer, not just the raw runtime. No mic/TTS needed.
 */
class RealLocalAIEngineDeviceTest {

    private fun engine(): LocalAIEngine {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        ensureModelOnDevice(ctx)
        val mm = DefaultModelManager(ctx)
        val rt: LocalLLMRuntime = LlamaCppRuntime(mm)
        return LocalAIEngine(mm, rt)
    }

    @Test
    fun streamResponse_emitsRealTokensThroughEngineStack() {
        runBlocking {
            val eng = engine()
            assertTrue("Model should be ready (real GGUF loaded)", eng.isModelReady())

            val systemPrompt = "You are Raya, a friendly, patient English conversation partner. " +
                "Keep replies to 1-2 short conversational spoken sentences. Ask a follow-up question."

            val tokens = eng.streamResponse(systemPrompt, emptyList(), "How are you today?").toList()
            val joined = tokens.joinToString("")
            Log.d(TAG, "STREAM TOKENS: ${tokens.size}")
            Log.d(TAG, "STREAM TEXT: $joined")

            assertTrue("Stream should emit at least one token (real generation)", tokens.isNotEmpty())
            assertTrue("Joined stream must not be blank (real generation)", joined.isNotBlank())
            assertTrue("Joined stream should be meaningful (>10 chars)", joined.length > 10)
            Log.d(TAG, "ENGINE STREAM VERIFIED")
        }
    }

    companion object {
        private const val TAG = "M17ENGINE"
    }
}
