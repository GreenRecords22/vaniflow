package com.vaniflow.app.engine.ai

import com.vaniflow.app.domain.model.SkillLevel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Level 3 contextual fallback that uses [ConversationalDialogueEngine] to generate
 * meaningful, conversational responses instead of static canned error phrases.
 */
@Singleton
class ContextAwareFallbackEngine @Inject constructor(
    private val dialogueEngine: ConversationalDialogueEngine
) : AIEngine {

    constructor() : this(ConversationalDialogueEngine())

    override suspend fun generateResponse(
        systemPrompt: String,
        conversationHistory: List<AITurn>,
        userInput: String
    ): AIResult {
        val startTime = System.currentTimeMillis()
        delay(20)
        val character = detectCharacter(systemPrompt)
        val text = dialogueEngine.generateResponse(
            characterId = character,
            scenarioTitle = "General Conversation",
            userLevel = SkillLevel.BEGINNER,
            history = conversationHistory,
            userInput = userInput
        )
        val latency = System.currentTimeMillis() - startTime
        return AIResult.Success(
            text = text,
            latencyMs = latency,
            metadata = AIResponseMetadata(
                routingLevel = AIRoutingLevel.CONTEXT_AWARE_FALLBACK,
                latencyMs = latency,
                tokensGenerated = ContextManager.estimateTokenCount(text),
                providerName = "VaniFlow Conversational Engine"
            )
        )
    }

    override fun streamResponse(
        systemPrompt: String,
        conversationHistory: List<AITurn>,
        userInput: String
    ): Flow<String> = flow {
        val result = generateResponse(systemPrompt, conversationHistory, userInput)
        if (result is AIResult.Success) {
            for (word in result.text.split(" ")) emit("$word ")
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
}