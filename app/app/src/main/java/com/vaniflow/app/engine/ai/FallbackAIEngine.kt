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
                0 -> "I'm having a brief connection hitch, but I heard you mention $subject! Could you repeat that once more?"
                1 -> "My connection dropped for a second! Let's keep practicing—what more can you tell me about $subject?"
                2 -> "Hey, I had trouble processing that complete thought. Could you say that in another way?"
                else -> "I missed the last part about $subject. Give it another shot so we can continue!"
            }
            "adwaita" -> when (index) {
                0 -> "I experienced a momentary connection interruption regarding $subject. Please restate your thought."
                1 -> "Our communication link paused briefly. How would you summarize your point about $subject again?"
                2 -> "I was unable to fully process that statement. Please rephrase so we may continue our session."
                else -> "Pardon the technical delay on $subject. Please articulate your thought once more."
            }
            "shub" -> when (index) {
                0 -> "A system processing delay occurred while analyzing $subject. Please restate your input."
                1 -> "The network interrupted our turn. What was your key point regarding $subject?"
                2 -> "I encountered an issue generating a full response. Could you rephrase your statement?"
                else -> "Please repeat your statement about $subject so we can analyze it properly."
            }
            else -> when (index) { // Raya default
                0 -> "I had a little trouble hearing the full thought about $subject. Could you tell me that again in another way?"
                1 -> "My connection flickered for a moment! I'd love to hear more about $subject—could you repeat that?"
                2 -> "I'm having a little trouble generating a response right now. Could you say that once more?"
                else -> "I want to make sure I understand you properly regarding $subject. Could you try saying that again?"
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
