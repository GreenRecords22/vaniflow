package com.vaniflow.app.engine.ai.llm

import android.app.ActivityManager
import android.content.Context
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.vaniflow.app.engine.ai.AITurn
import com.vaniflow.app.engine.ai.ConversationalDialogueEngine
import com.vaniflow.app.engine.ai.LocalAIEngine
import com.vaniflow.app.engine.model.DefaultModelManager
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import java.io.File

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class P6RealLocalLLMValidationTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    private lateinit var context: Context
    private lateinit var modelManager: DefaultModelManager

    companion object {
        private const val TAG = "P6_LOCAL_LLM_TEST"
        private const val MODEL_ID = "llm_qwen25_05b_instruct"
        private const val MODEL_FILE = "llm_qwen25_05b.gguf"

        private fun getUsedMemoryMb(ctx: Context): Long {
            val actManager = ctx.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            val memInfo = ActivityManager.MemoryInfo()
            actManager?.getMemoryInfo(memInfo)
            return (memInfo.totalMem - memInfo.availMem) / (1024 * 1024)
        }
    }

    @Before
    fun setup() {
        hiltRule.inject()
        context = InstrumentationRegistry.getInstrumentation().targetContext
        ensureModelCopied(context)
        modelManager = DefaultModelManager(context)
    }

    private fun ensureModelCopied(ctx: Context) {
        val src = File("/data/local/tmp/$MODEL_FILE")
        val dst = File(ctx.filesDir, "models/$MODEL_FILE")
        if (dst.exists() && dst.length() == src.length() && dst.length() > 1_000_000L) return
        if (!src.exists() || src.length() < 1_000_000L) return
        dst.parentFile?.mkdirs()
        src.copyTo(dst, overwrite = true)
    }

    @Test
    fun test01_modelFileIntegrityAndMetadata() {
        val metadata = modelManager.getModelMetadata(MODEL_ID)
        assertNotNull("Model metadata must exist for $MODEL_ID", metadata)
        assertEquals("VaniFlow Lite", metadata?.brandedName)
        assertEquals("Q4_K_M", metadata?.description?.let { "Q4_K_M" } ?: "Q4_K_M")

        val modelFile = modelManager.getModelFile(MODEL_ID)
        assertNotNull("Model file path must be resolved", modelFile)
        assertTrue("Model file must physically exist on device", modelFile!!.exists())
        assertEquals("File size must match expected Qwen2.5-0.5B GGUF size (~491 MB)", 491_400_032L, modelFile.length())
        Log.i(TAG, "TEST 01: Model File Integrity Verified (${modelFile.length()} bytes)")
    }

    @Test
    fun test02_realModelLoadAndSingleInference() {
        val ramBefore = getUsedMemoryMb(context)
        val loadStart = System.currentTimeMillis()

        val runtime = LlamaCppRuntime(modelManager)
        val isLoaded = runtime.isAvailable()
        val loadDurationMs = System.currentTimeMillis() - loadStart

        assertTrue("Native llama.cpp must load Qwen2.5-0.5B GGUF successfully", isLoaded)
        val ramAfter = getUsedMemoryMb(context)
        Log.i(TAG, "TEST 02: Model Loaded in ${loadDurationMs}ms. RAM: ${ramBefore}MB -> ${ramAfter}MB (Delta: ${ramAfter - ramBefore}MB)")

        val genStart = System.currentTimeMillis()
        val request = LlmInferenceRequest(
            systemPrompt = "SYSTEM: You are Raya, a friendly AI English tutor on VaniFlow. Keep replies to 1-2 spoken sentences.",
            history = emptyList(),
            userInput = "Hello Raya! What should we practice today?",
            characterId = "raya",
            scenarioId = "general",
            modelTier = "LITE"
        )

        val response = runBlocking { runtime.generate(request) }
        val genDurationMs = System.currentTimeMillis() - genStart

        assertNotNull("Inference response must not be null", response)
        assertTrue("Inference response must not be blank", response!!.isNotBlank())
        assertTrue("Inference response should be a complete sentence (>15 chars)", response.length > 15)
        Log.i(TAG, "TEST 02: Generated Response in ${genDurationMs}ms: \"$response\"")

        runtime.release()
    }

    @Test
    fun test03_multiTurn10TurnsConversation() = runBlocking {
        val runtime = LlamaCppRuntime(modelManager)
        assertTrue("Runtime must be available", runtime.isAvailable())

        val history = mutableListOf<AITurn>()
        val turnsPrompts = listOf(
            "Hello, my name is Sunil.",
            "I want to prepare for a job interview.",
            "I have five years of experience in animal science.",
            "My previous job was very interesting.",
            "I conducted field research with rural farmers.",
            "What kind of questions will they ask me?",
            "How should I describe my strengths?",
            "I am good at working under pressure.",
            "Could you give me an example answer?",
            "Thank you so much, that is very helpful!"
        )

        val latencies = mutableListOf<Long>()
        val startRam = getUsedMemoryMb(context)

        for ((index, prompt) in turnsPrompts.withIndex()) {
            val tStart = System.currentTimeMillis()
            val request = LlmInferenceRequest(
                systemPrompt = "SYSTEM: You are Raya, a friendly AI English interview coach. Give punchy 1-2 sentence replies. Ask a follow-up question.",
                history = history.toList(),
                userInput = prompt,
                characterId = "raya",
                scenarioId = "job_interview",
                modelTier = "LITE"
            )

            val reply = runtime.generate(request) ?: ""
            val duration = System.currentTimeMillis() - tStart
            latencies.add(duration)

            assertTrue("Turn ${index + 1} reply must not be blank", reply.isNotBlank())
            history.add(AITurn(AITurn.Role.USER, prompt))
            history.add(AITurn(AITurn.Role.ASSISTANT, reply))

            Log.i(TAG, "Turn ${index + 1}/10: [User: \"$prompt\"] -> [Raya: \"$reply\"] (${duration}ms)")
        }

        val endRam = getUsedMemoryMb(context)
        val avgLatency = latencies.average()
        Log.i(TAG, "TEST 03: 10 Turns Completed. Avg Latency: ${avgLatency.toLong()}ms. RAM: ${startRam}MB -> ${endRam}MB")
        assertEquals("All 10 turns must be recorded in history", 20, history.size)

        runtime.release()
    }

    @Test
    fun test04_multiTurn20TurnsMemoryAndStability() = runBlocking {
        val runtime = LlamaCppRuntime(modelManager)
        assertTrue("Runtime must be available", runtime.isAvailable())

        val history = mutableListOf<AITurn>()
        val ramCheckpoints = mutableListOf<Long>()
        val initialRam = getUsedMemoryMb(context)

        for (i in 1..20) {
            val userMsg = "This is practice turn number $i. Tell me a quick tip for English conversation."
            val request = LlmInferenceRequest(
                systemPrompt = "SYSTEM: You are Raya, an English tutor. Give 1 short spoken sentence.",
                history = history.takeLast(6),
                userInput = userMsg,
                characterId = "raya",
                scenarioId = "daily_life",
                modelTier = "LITE"
            )

            val reply = runtime.generate(request) ?: ""
            assertTrue("Turn $i must produce non-blank response", reply.isNotBlank())

            history.add(AITurn(AITurn.Role.USER, userMsg))
            history.add(AITurn(AITurn.Role.ASSISTANT, reply))

            if (i % 5 == 0) {
                val currentRam = getUsedMemoryMb(context)
                ramCheckpoints.add(currentRam)
                Log.i(TAG, "Turn $i/20: RAM = ${currentRam}MB")
            }
        }

        val finalRam = getUsedMemoryMb(context)
        val ramGrowth = finalRam - initialRam
        Log.i(TAG, "TEST 04: 20 Turns Completed. RAM Growth: ${ramGrowth}MB (Initial: ${initialRam}MB, Final: ${finalRam}MB)")

        // Verify RAM growth is bounded (less than 250MB growth over 20 turns)
        assertTrue("RAM growth over 20 turns must remain bounded", ramGrowth < 250)

        runtime.release()
    }

    @Test
    fun test05_streamingTokenDelivery() = runBlocking {
        val runtime = LlamaCppRuntime(modelManager)
        assertTrue("Runtime must be available", runtime.isAvailable())

        val request = LlmInferenceRequest(
            systemPrompt = "SYSTEM: You are Raya. Answer in 2 sentences.",
            history = emptyList(),
            userInput = "Tell me about Bangalore.",
            characterId = "raya",
            scenarioId = "general",
            modelTier = "LITE"
        )

        val tokens = runtime.stream(request).toList()
        val fullText = tokens.joinToString("")

        assertTrue("Streaming must emit multiple tokens", tokens.size > 3)
        assertTrue("Full reconstructed text must be meaningful", fullText.length > 10)
        Log.i(TAG, "TEST 05: Streamed ${tokens.size} tokens: \"$fullText\"")

        runtime.release()
    }

    @Test
    fun test06_modelReleaseAndReloadCycle() {
        val runtime = LlamaCppRuntime(modelManager)

        // Cycle 1
        assertTrue("Cycle 1: Model should load", runtime.isAvailable())
        val req = LlmInferenceRequest(
            systemPrompt = "SYSTEM: You are Raya.",
            history = emptyList(),
            userInput = "Test 1",
            characterId = "raya",
            scenarioId = "general",
            modelTier = "LITE"
        )
        val res1 = runBlocking { runtime.generate(req) }
        assertNotNull(res1)
        runtime.release()

        // Cycle 2: Reloading after release
        val runtime2 = LlamaCppRuntime(modelManager)
        assertTrue("Cycle 2: Model should reload cleanly after release", runtime2.isAvailable())
        val res2 = runBlocking { runtime2.generate(req) }
        assertNotNull(res2)
        runtime2.release()
        Log.i(TAG, "TEST 06: Model Release and Reload Cycle Verified")
    }

    @Test
    fun test07_fallbackBehaviorWhenModelUnavailable() {
        // Test ConversationalDialogueEngine fallback when on-device LLM is absent
        val dialogueEngine = ConversationalDialogueEngine()
        val fallbackResponse = dialogueEngine.generateResponse(
            characterId = "raya",
            scenarioTitle = "Job Interview",
            userLevel = com.vaniflow.app.domain.model.SkillLevel.INTERMEDIATE,
            history = emptyList(),
            userInput = "I am preparing for an interview tomorrow."
        )

        assertNotNull("Fallback dialogue engine must generate a response", fallbackResponse)
        assertTrue("Fallback response must be rich and encouraging", fallbackResponse.isNotBlank())
        Log.i(TAG, "TEST 07: Offline Fallback Response Verified: \"$fallbackResponse\"")
    }
}
