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
 * Google Gemini REST API adapter supporting generateContent and SSE streaming.
 */
@Singleton
class GeminiProviderAdapter @Inject constructor() : CloudProviderAdapter {

    override val adapterType: String = "gemini"
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
            val urlString = if (endpoint.contains("key=")) endpoint else "$endpoint?key=$apiKey"
            val url = URL(urlString)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = timeoutMs.toInt()
                readTimeout = timeoutMs.toInt()
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }

            val payload = buildGeminiPayload(systemPrompt, history, userInput)
            OutputStreamWriter(connection.outputStream, "UTF-8").use { it.write(payload) }

            val responseCode = connection.responseCode
            if (responseCode == 429) {
                return@withContext AIResult.Error("Gemini quota rate limit exceeded (HTTP 429)")
            }
            if (responseCode !in 200..299) {
                val err = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $responseCode"
                return@withContext AIResult.Error("Gemini API error ($responseCode): $err")
            }

            val responseBody = BufferedReader(InputStreamReader(connection.inputStream, "UTF-8")).use { it.readText() }
            val parsedJson = json.parseToJsonElement(responseBody).jsonObject
            val text = parsedJson["candidates"]?.jsonArray?.firstOrNull()?.jsonObject?.get("content")?.jsonObject?.get("parts")?.jsonArray?.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.content
                ?: return@withContext AIResult.Error("No content returned in Gemini candidates")

            val latency = System.currentTimeMillis() - startTime
            val tokens = ContextManager.estimateTokenCount(text)

            AIResult.Success(
                text = text.trim(),
                latencyMs = latency,
                metadata = AIResponseMetadata(
                    routingLevel = AIRoutingLevel.OPTIONAL_CLOUD,
                    latencyMs = latency,
                    tokensGenerated = tokens,
                    providerName = "Gemini Flash"
                )
            )
        } catch (e: Exception) {
            AIResult.Error("Gemini network error: ${e.message}")
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
        val streamEndpoint = endpoint.replace(":generateContent", ":streamGenerateContent") + "?alt=sse&key=$apiKey"
        val url = URL(streamEndpoint)
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = timeoutMs.toInt()
            readTimeout = (timeoutMs * 2).toInt()
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
        }

        val payload = buildGeminiPayload(systemPrompt, history, userInput)
        OutputStreamWriter(connection.outputStream, "UTF-8").use { it.write(payload) }

        val responseCode = connection.responseCode
        if (responseCode !in 200..299) {
            throw RuntimeException("Gemini streaming error HTTP $responseCode")
        }

        BufferedReader(InputStreamReader(connection.inputStream, "UTF-8")).use { reader ->
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val currentLine = line?.trim() ?: continue
                if (currentLine.startsWith("data: ")) {
                    val data = currentLine.removePrefix("data: ").trim()
                    try {
                        val parsed = json.parseToJsonElement(data).jsonObject
                        val text = parsed["candidates"]?.jsonArray?.firstOrNull()?.jsonObject?.get("content")?.jsonObject?.get("parts")?.jsonArray?.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.content
                        if (!text.isNullOrEmpty()) {
                            emit(text)
                        }
                    } catch (_: Exception) {}
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    private fun buildGeminiPayload(
        systemPrompt: String,
        history: List<AITurn>,
        userInput: String
    ): String {
        val contents = buildJsonArray {
            for (turn in history) {
                addJsonObject {
                    put("role", if (turn.role == AITurn.Role.USER) "user" else "model")
                    put("parts", buildJsonArray {
                        addJsonObject { put("text", turn.content) }
                    })
                }
            }
            addJsonObject {
                put("role", "user")
                put("parts", buildJsonArray {
                    addJsonObject { put("text", userInput) }
                })
            }
        }

        return buildJsonObject {
            if (systemPrompt.isNotBlank()) {
                put("systemInstruction", buildJsonObject {
                    put("parts", buildJsonArray {
                        addJsonObject { put("text", systemPrompt) }
                    })
                })
            }
            put("contents", contents)
            put("generationConfig", buildJsonObject {
                put("temperature", 0.7)
                put("maxOutputTokens", 256)
            })
        }.toString()
    }
}