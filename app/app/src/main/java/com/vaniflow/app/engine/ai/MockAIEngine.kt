package com.vaniflow.app.engine.ai

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mock AI Engine for testing and vertical-slice validation.
 * Simulates intelligent conversational responses with realistic delays.
 */
@Singleton
class MockAIEngine @Inject constructor() : AIEngine {

    private val contextualResponses = listOf(
        "That's really interesting! How long have you been doing that?",
        "Sounds great! Tell me more about what you like most about it.",
        "I understand. What do you think would be the best next step?",
        "Oh nice! That sounds like a wonderful experience. Would you recommend it to others?",
        "I see what you mean. In a situation like that, how do you usually respond?",
        "Great point! Speaking of which, have you noticed any major changes recently?"
    )

    private var responseIndex = 0

    override suspend fun generateResponse(
        systemPrompt: String,
        conversationHistory: List<AITurn>,
        userInput: String
    ): AIResult {
        // Simulate inference latency (400ms)
        delay(400)

        val response = getNextResponse(userInput)
        return AIResult.Success(response, latencyMs = 400)
    }

    override fun streamResponse(
        systemPrompt: String,
        conversationHistory: List<AITurn>,
        userInput: String
    ): Flow<String> = flow {
        val fullResponse = getNextResponse(userInput)
        val words = fullResponse.split(" ")
        for (word in words) {
            delay(80)
            emit("$word ")
        }
    }

    private fun getNextResponse(userInput: String): String {
        val lower = userInput.lowercase()
        return when {
            lower.contains("coffee") || lower.contains("latte") || lower.contains("cappuccino") ->
                "Sure thing! One warm coffee coming right up. Would you like oat milk or regular whole milk with that?"
            lower.contains("interview") || lower.contains("experience") || lower.contains("job") ->
                "Thank you for sharing. Could you describe a challenging project you worked on and how you handled obstacles?"
            lower.contains("jaipur") || lower.contains("delhi") || lower.contains("city") ->
                "Jaipur is vibrant and full of culture! How long have you lived there, and what is your favorite part of the city?"
            lower.contains("hello") || lower.contains("hi") ->
                "Hello there! It's so nice to meet you. What would you like to chat about today?"
            else -> {
                val resp = contextualResponses[responseIndex % contextualResponses.size]
                responseIndex++
                resp
            }
        }
    }
}
