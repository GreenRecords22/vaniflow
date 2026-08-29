package com.vaniflow.app.engine.ai.provider.adapter

import com.vaniflow.app.engine.ai.AIResult
import com.vaniflow.app.engine.ai.AITurn
import kotlinx.coroutines.flow.Flow

/**
 * Standard contract for real cloud AI provider adapters (Gemini, Groq, OpenRouter, Gateway).
 */
interface CloudProviderAdapter {
    val adapterType: String

    suspend fun generate(
        endpoint: String,
        apiKey: String,
        model: String,
        systemPrompt: String,
        history: List<AITurn>,
        userInput: String,
        timeoutMs: Long
    ): AIResult

    fun stream(
        endpoint: String,
        apiKey: String,
        model: String,
        systemPrompt: String,
        history: List<AITurn>,
        userInput: String,
        timeoutMs: Long
    ): Flow<String>
}