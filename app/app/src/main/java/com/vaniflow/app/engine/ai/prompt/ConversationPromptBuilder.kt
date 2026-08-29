package com.vaniflow.app.engine.ai.prompt

import com.vaniflow.app.domain.model.SkillLevel
import com.vaniflow.app.engine.ai.AITurn

/**
 * Builds the full, structured conversation prompt sent to a real local model.
 *
 * Phase 4 / Phase 6 of M16 & Milestone 20: every request carries SYSTEM identity,
 * character personality, user level, scenario, rolling conversation history, the latest
 * user message, a learning objective, and natural response rules.
 */
object ConversationPromptBuilder {

    fun buildRuntimePrompt(
        characterName: String,
        personalityPrompt: String,
        scenarioTitle: String,
        scenarioPrompt: String,
        userLevel: SkillLevel,
        history: List<AITurn>,
        userInput: String,
        learningObjective: String = "Speak naturally and build confidence.",
        tutoringContext: String? = null
    ): String = buildString {
        appendLine("SYSTEM: You are $characterName, an AI English conversation partner on VaniFlow.")
        if (!tutoringContext.isNullOrBlank()) {
            appendLine()
            appendLine(tutoringContext.trim())
        }
        appendLine()
        appendLine("CHARACTER PERSONALITY:")
        appendLine(personalityPrompt.trim())
        appendLine()
        appendLine("USER LEVEL: ${userLevel.name.lowercase().replace('_', ' ')}")
        appendLine()
        appendLine("SCENARIO: $scenarioTitle")
        appendLine(scenarioPrompt.trim())
        appendLine()
        appendLine("CONVERSATION HISTORY:")
        if (history.isEmpty()) {
            appendLine("(no previous turns)")
        } else {
            history.takeLast(8).forEach { turn ->
                val role = when (turn.role) {
                    AITurn.Role.USER -> "User"
                    AITurn.Role.ASSISTANT -> characterName
                    else -> "System"
                }
                appendLine("$role: ${turn.content}")
            }
        }
        appendLine()
        appendLine("LEARNING OBJECTIVE: $learningObjective")
        appendLine()
        appendLine("LATEST USER MESSAGE:")
        appendLine(userInput)
        appendLine()
        appendLine("RESPONSE RULES:")
        appendLine("- Respond directly, authentically, and contextually to the user's latest message above.")
        appendLine("- Start with a natural reaction, acknowledgement, or emotional touch where fitting (e.g., 'Oh really?', 'That's interesting!', 'Honestly, I love...', 'That sounds fun 😄').")
        appendLine("- Never reuse a canned response; do not repeat your previous answer.")
        appendLine("- Maintain conversation context and the current topic.")
        appendLine("- Ask an engaging follow-up question ONLY when appropriate to keep the dialogue flowing naturally. Do not force a question if the user is making a concluding statement.")
        appendLine("- Keep spoken replies punchy and natural (1-3 spoken sentences).")
        appendLine("- Avoid textbook explanations; talk like a real human speaking partner.")
        appendLine("- If the user's message uses pronouns (it, that, there, this) or is a short follow-up like 'why' or 'tell me more', resolve them from the CONVERSATION HISTORY above instead of asking what they mean.")
        appendLine("- If the user changes the topic, follow the new topic naturally while staying in character.")
        appendLine("- Match the tone, energy, and speaking style of your assigned character personality above.")
        appendLine("- Stay in character as $characterName at all times.")
    }.trim()

    /** Stronger instruction used when the first generation is detected as a repeat. */
    fun buildRegenerationPrompt(basePrompt: String): String =
        "$basePrompt\n\nIMPORTANT: Your previous answer repeated an earlier response or ignored the question. " +
            "Give a DIFFERENT, relevant answer that directly addresses the latest user message."
}