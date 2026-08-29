package com.vaniflow.app.engine.ai.provider.adapter

import com.vaniflow.app.engine.ai.AIResponseMetadata
import com.vaniflow.app.engine.ai.AIResult
import com.vaniflow.app.engine.ai.AIRoutingLevel
import com.vaniflow.app.engine.ai.AITurn
import com.vaniflow.app.engine.ai.ContextManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Universal OpenAI-compatible Chat Completions adapter for Groq, OpenRouter, and custom gateways.
 * Supports standard JSON completion and Server-Sent Events (SSE) token streaming.
 */
@Singleton
class OpenAICompatibleAdapter @Inject constructor() : CloudProviderAdapter {

    override val adapterType: String = "openai_compatible"
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    override suspend fun generate(
        endpoint: String,
        apiKey: String,
        model: String,
        systemPrompt: String,
        history: List<AITurn>,
        userInput: String,
        timeoutMs: Long
    ): AIResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        try {
            val url = URL(endpoint)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = timeoutMs.toInt()
                readTimeout = timeoutMs.toInt()
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                if (apiKey.isNotBlank()) {
                    setRequestProperty("Authorization", "Bearer $apiKey")
                }
            }

            val payload = buildRequestPayload(model, systemPrompt, history, userInput, stream = false)
            OutputStreamWriter(connection.outputStream, "UTF-8").use { it.write(payload) }

            val responseCode = connection.responseCode
            if (responseCode == 429) {
                return@withContext AIResult.Error("Rate limit exceeded (HTTP 429)")
            }
            if (responseCode !in 200..299) {
                val err = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $responseCode"
                return@withContext AIResult.Error("API request failed with code $responseCode: $err")
            }

            val responseBody = BufferedReader(InputStreamReader(connection.inputStream, "UTF-8")).use { it.readText() }
            val parsedJson = json.parseToJsonElement(responseBody).jsonObject
            val content = parsedJson["choices"]?.jsonArray?.firstOrNull()?.jsonObject?.get("message")?.jsonObject?.get("content")?.jsonPrimitive?.content
                ?: return@withContext AIResult.Error("Empty response content from provider")

            val latency = System.currentTimeMillis() - startTime
            val tokens = ContextManager.estimateTokenCount(content)

            AIResult.Success(
                text = content.trim(),
                latencyMs = latency,
                metadata = AIResponseMetadata(
                    routingLevel = AIRoutingLevel.OPTIONAL_CLOUD,
                    latencyMs = latency,
                    tokensGenerated = tokens,
                    providerName = model
                )
            )
        } catch (e: Exception) {
            AIResult.Error("Network error: ${e.message}")
        }
    }

    override fun stream(
        endpoint: String,
        apiKey: String,
        model: String,
        systemPrompt: String,
        history: List<AITurn>,
        userInput: String,
        timeoutMs: Long
    ): Flow<String> = flow {
        val url = URL(endpoint)
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = timeoutMs.toInt()
            readTimeout = (timeoutMs * 2).toInt()
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            if (apiKey.isNotBlank()) {
                setRequestProperty("Authorization", "Bearer $apiKey")
            }
        }

        val payload = buildRequestPayload(model, systemPrompt, history, userInput, stream = true)
        OutputStreamWriter(connection.outputStream, "UTF-8").use { it.write(payload) }

        val responseCode = connection.responseCode
        if (responseCode !in 200..299) {
            throw RuntimeException("HTTP $responseCode from streaming endpoint")
        }

        BufferedReader(InputStreamReader(connection.inputStream, "UTF-8")).use { reader ->
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val currentLine = line?.trim() ?: continue
                if (currentLine.startsWith("data: ")) {
                    val data = currentLine.removePrefix("data: ").trim()
                    if (data == "[DONE]") break
                    try {
                        val obj = json.parseToJsonElement(data).jsonObject
                        val deltaContent = obj["choices"]?.jsonArray?.firstOrNull()?.jsonObject?.get("delta")?.jsonObject?.get("content")?.jsonPrimitive?.content
                        if (!deltaContent.isNullOrEmpty()) {
                            emit(deltaContent)
                        }
                    } catch (_: Exception) {
                        // Skip malformed SSE chunks gracefully
                    }
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    private fun buildRequestPayload(
        model: String,
        systemPrompt: String,
        history: List<AITurn>,
        userInput: String,
        stream: Boolean
    ): String {
        val messages = buildJsonArray {
            if (systemPrompt.isNotBlank()) {
                addJsonObject {
                    put("role", "system")
                    put("content", systemPrompt)
                }
            }
            for (turn in history) {
                addJsonObject {
                    put("role", if (turn.role == AITurn.Role.USER) "user" else "assistant")
                    put("content", turn.content)
                }
            }
            addJsonObject {
                put("role", "user")
                put("content", userInput)
            }
        }

        return buildJsonObject {
            put("model", model)
            put("messages", messages)
            put("temperature", 0.7)
            put("max_tokens", 256)
            put("stream", stream)
        }.toString()
    }
}