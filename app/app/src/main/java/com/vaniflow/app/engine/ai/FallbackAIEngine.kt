package com.vaniflow.app.engine.ai

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Minimal Emergency Safe Fallback Engine.
 *
 * This engine is strictly a LAST-RESORT SAFETY NET used only when Cloud AI,
 * Secondary Cloud, and Local Qwen SLM are completely unavailable or failed quality checks.
 *
 * It contains NO hardcoded topic matrices or canned chatbot branching.
 * It uses 4 minimal context-preserving emergency templates.
 */
@Singleton
class FallbackAIEngine @Inject constructor() : AIEngine {

    override suspend fun generateResponse(
        systemPrompt: String,
        conversationHistory: List<AITurn>,
        userInput: String
    ): AIResult {
        val startTime = System.currentTimeMillis()
        delay(30)

        val character = detectCharacter(systemPrompt)
        val response = generateEmergencyFallback(userInput, character, conversationHistory)
        val latency = System.currentTimeMillis() - startTime

        return AIResult.Success(
            text = response,
            latencyMs = latency,
            metadata = AIResponseMetadata(
                routingLevel = AIRoutingLevel.EMERGENCY_FALLBACK,
                latencyMs = latency,
                tokensGenerated = ContextManager.estimateTokenCount(response),
                providerName = "VaniFlow Emergency Fallback",
                fallbackReason = "All AI models unavailable"
            )
        )
    }

    override fun streamResponse(
        systemPrompt: String,
        conversationHistory: List<AITurn>,
        userInput: String
    ): Flow<String> = flow {
        val character = detectCharacter(systemPrompt)
        val fullResponse = generateEmergencyFallback(userInput, character, conversationHistory)
        val words = fullResponse.split(" ")
        for (word in words) {
            delay(20)
            emit("$word ")
        }
    }

    private fun detectCharacter(systemPrompt: String): String {
        val lower = systemPrompt.lowercase()
        return when {
            lower.contains("adwaita") -> "adwaita"
            lower.contains("rudra") -> "rudra"
            lower.contains("shub") -> "shub"
            else -> "raya"
        }
    }

    /**
     * Produces 1 of 4 minimal context-preserving emergency responses.
     */
    private fun generateEmergencyFallback(
        rawInput: String,
        character: String,
        history: List<AITurn>
    ): String {
        val input = rawInput.trim()
        val subject = extractSubject(input)
        val index = (input.hashCode() + history.size).let { if (it < 0) -it else it } % 4

        return when (character) {
            "rudra" -> when (index) {
                0 -> "Hey! I need an active AI connection to answer about $subject. Please enter your free Groq API key in Profile > AI Cloud & Tutor Engine!"
                1 -> "My AI engine needs an API key! Head over to Profile settings to paste your Groq or Gemini key, or get an offline model."
                2 -> "To chat about $subject with real AI, please enter your Groq API key in Profile settings."
                else -> "Connect your Groq or Gemini API key in Profile > AI Cloud & Tutor Engine to level up our conversation on $subject!"
            }
            "adwaita" -> when (index) {
                0 -> "An active AI engine is required to discuss $subject. Please configure your Groq or Gemini API credentials in Profile Settings."
                1 -> "Please provide a valid API key in Profile > AI Cloud & Tutor Engine to enable AI-powered dialogue."
                2 -> "AI connection unconfigured. Kindly input your Groq or Gemini API key in Profile settings."
                else -> "To proceed with our discussion on $subject, please configure your AI provider in Profile settings."
            }
            "shub" -> when (index) {
                0 -> "AI engine is not configured. Please input your Groq API key in Profile > AI Cloud & Tutor Engine."
                1 -> "Please enter your AI provider credentials in Profile settings to analyze $subject."
                2 -> "No active AI provider detected. Please set up your Groq key in Profile settings."
                else -> "Please configure your Groq API key in Profile settings so we can continue."
            }
            else -> when (index) { // Raya default
                0 -> "I need an active AI connection to answer about $subject. Please open Profile > AI Cloud & Tutor Engine to enter your free Groq API key, or download an offline model."
                1 -> "To practice with full AI answers on $subject, please add your Groq API key in Profile settings or download an offline speaking model."
                2 -> "My AI engine is currently unconfigured. Please enter your Groq or Gemini API key in Profile settings to chat."
                else -> "Please configure your Groq API key in Profile > AI Cloud & Tutor Engine so I can give you complete spoken answers about $subject."
            }
        }
    }

    private fun extractSubject(input: String): String {
        val clean = input.replace("[?!.,]".toRegex(), "").trim()
        val words = clean.split("\\s+".toRegex()).filter { it.isNotBlank() }
        if (words.isEmpty()) return "that"

        val stopWords = setOf(
            "i", "me", "my", "myself", "we", "our", "you", "your", "he", "she", "it", "they",
            "the", "a", "an", "this", "that", "these", "those", "is", "are", "was", "were",
            "be", "been", "being", "have", "has", "had", "do", "does", "did", "can", "could",
            "will", "would", "shall", "should", "may", "might", "must", "and", "but", "or",
            "so", "if", "because", "as", "until", "while", "of", "at", "by", "for", "with",
            "about", "against", "between", "into", "through", "during", "before", "after",
            "above", "below", "to", "from", "up", "down", "in", "out", "on", "off", "over",
            "under", "again", "further", "then", "once", "here", "there", "when", "where",
            "why", "how", "all", "any", "both", "each", "few", "more", "most", "other",
            "some", "such", "no", "nor", "not", "only", "own", "same", "than", "too", "very",
            "just", "tell", "say", "speak"
        )

        val meaningful = words.filter { !stopWords.contains(it.lowercase()) }
        return when {
            meaningful.size >= 2 -> meaningful.takeLast(2).joinToString(" ")
            meaningful.size == 1 -> meaningful.first()
            words.size >= 2 -> words.takeLast(2).joinToString(" ")
            else -> "that"
        }
    }
}
