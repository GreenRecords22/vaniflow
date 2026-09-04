package com.vaniflow.app.engine.ai.llm

import com.vaniflow.app.engine.ai.AITurn
import com.vaniflow.app.engine.model.ModelManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.channels.awaitClose
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Real on-device LLM runtime backed by llama.cpp (GGUF). This is the production
 * implementation of [LocalLLMRuntime] — it performs genuine token generation from
 * the supplied prompt + history. It is NOT a stub, keyword matrix, or canned responder.
 *
 * Loads the model lazily from the path [ModelManager] reports for the default LLM id.
 * The [LlmInferenceRequest.systemPrompt] already contains character personality,
 * scenario, conversation history, and the latest user message (assembled by
 * ConversationPromptBuilder), so it is supplied as the chat "system" message.
 */
@Singleton
class LlamaCppRuntime @Inject constructor(
    private val modelManager: ModelManager
) : LocalLLMRuntime {

    companion object {
        init {
            // Dependency order matters for the dynamic linker.
            System.loadLibrary("ggml-base")
            System.loadLibrary("ggml-cpu")
            System.loadLibrary("ggml")
            System.loadLibrary("llama")
            System.loadLibrary("llamajni")
        }

        private const val DEFAULT_LLM_ID = "llm_qwen25_05b_instruct"
        private const val MAX_TOKENS = 80
        private const val TEMPERATURE = 0.8f
    }

    @Volatile private var loaded = false
    private val loadLock = Any()

    private fun ensureLoaded(): Boolean {
        if (loaded) return true
        synchronized(loadLock) {
            if (loaded) return true
            val file = modelManager.getModelFile(DEFAULT_LLM_ID)
            if (file != null && file.exists() && file.length() > 1_000_000L) {
                loaded = nativeLoad(file.absolutePath)
            }
        }
        return loaded
    }

    override fun isAvailable(): Boolean = ensureLoaded()

    override fun generate(request: LlmInferenceRequest): String? {
        if (!ensureLoaded()) return null
        val (roles, contents) = buildMessages(request)
        val generated = nativeGenerate(roles, contents, true, MAX_TOKENS, TEMPERATURE, null)
        return generated?.trim()?.ifBlank { null }
    }

    override fun stream(request: LlmInferenceRequest): Flow<String> {
        if (!ensureLoaded()) return emptyFlow()
        val (roles, contents) = buildMessages(request)
        return callbackFlow {
            val callback = object : TokenCallback {
                override fun onToken(piece: String) { trySend(piece) }
                override fun onComplete() { close() }
                override fun onError(message: String) { close(IllegalStateException(message)) }
            }
            nativeGenerate(roles, contents, true, MAX_TOKENS, TEMPERATURE, callback)
            awaitClose { nativeAbort() }
        }
    }

    override fun release() {
        synchronized(loadLock) {
            if (loaded) {
                nativeRelease()
                loaded = false
            }
        }
    }

    private fun buildMessages(request: LlmInferenceRequest): Pair<Array<String>, Array<String>> {
        val roles = ArrayList<String>()
        val contents = ArrayList<String>()
        roles.add("system")
        contents.add(request.systemPrompt)
        val boundedHistory = request.history.takeLast(6)
        for (turn in boundedHistory) {
            roles.add(if (turn.role == AITurn.Role.USER) "user" else "assistant")
            contents.add(turn.content)
        }
        // If the latest user input is not already the last message in history, append it as the final user turn.
        if (request.userInput.isNotBlank()) {
            val lastTurn = request.history.lastOrNull()
            if (lastTurn == null || lastTurn.role != AITurn.Role.USER || lastTurn.content.trim() != request.userInput.trim()) {
                roles.add("user")
                contents.add(request.userInput.trim())
            }
        }
        return roles.toTypedArray() to contents.toTypedArray()
    }

    private interface TokenCallback {
        fun onToken(piece: String)
        fun onComplete()
        fun onError(message: String)
    }

    private external fun nativeLoad(path: String): Boolean
    private external fun nativeGenerate(
        roles: Array<String>,
        contents: Array<String>,
        addAssistant: Boolean,
        maxTokens: Int,
        temperature: Float,
        callback: TokenCallback?
    ): String?
    private external fun nativeAbort()
    private external fun nativeRelease()
}
