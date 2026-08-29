package com.vaniflow.app.engine.ai

import com.vaniflow.app.domain.model.SkillLevel

/**
 * Manages conversational context budget, memory compaction, and prompt assembly.
 * Prevents context window explosion, saves on-device RAM, and accelerates inference.
 */
object ContextManager {

    private const val MAX_HISTORY_TURNS = 6 // Keeps last 6 turns (3 exchanges) for tight latency
    private const val ESTIMATED_CHARS_PER_TOKEN = 4

    data class ConversationContext(
        val characterName: String,
        val personalityPrompt: String,
        val scenarioTitle: String,
        val scenarioPrompt: String,
        val targetVocabulary: List<String> = emptyList(),
        val userLevel: SkillLevel = SkillLevel.INTERMEDIATE,
        val maxTokensBudget: Int = 512
    )

    /**
     * Assembles a compact, high-impact system prompt for on-device SLMs.
     */
    fun buildSystemPrompt(context: ConversationContext): String = buildString {
        appendLine(context.personalityPrompt.trim())
        appendLine("Scenario: ${context.scenarioTitle}. ${context.scenarioPrompt.trim()}")
        if (context.targetVocabulary.isNotEmpty()) {
            appendLine("Target words: ${context.targetVocabulary.take(4).joinToString(", ")}.")
        }
        appendLine("Rules: Respond as ${context.characterName}. 1 to 2 short conversational sentences only. Ask a natural follow-up question. Do not lecture.")
    }.trim()

    /**
     * Compacts history turns to fit within the designated token budget.
     */
    fun compactHistory(
        history: List<AITurn>,
        maxTurns: Int = MAX_HISTORY_TURNS
    ): List<AITurn> {
        if (history.size <= maxTurns) return history
        return history.takeLast(maxTurns)
    }

    /**
     * Estimates token count of a given text string (~4 chars/token).
     */
    fun estimateTokenCount(text: String): Int {
        if (text.isBlank()) return 0
        return (text.length / ESTIMATED_CHARS_PER_TOKEN).coerceAtLeast(1)
    }
}
