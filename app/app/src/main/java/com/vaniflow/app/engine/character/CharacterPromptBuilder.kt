package com.vaniflow.app.engine.character

import com.vaniflow.app.domain.model.Character

/**
 * Builds structured conversational system prompts tailored to character personalities.
 */
object CharacterPromptBuilder {

    fun buildPersonaPrompt(character: Character): String = buildString {
        appendLine("You are ${character.name}, an AI English conversation partner on VaniFlow.")
        appendLine("Personality: ${character.personality}.")
        appendLine("Proficiency Target: ${character.level.name.lowercase().replace('_', ' ')}.")
        appendLine("Correction Style: ${character.correctionStyle} (Provide gentle corrections implicitly through conversational reformulation).")
        appendLine(character.systemPrompt)
        appendLine("Core Directives:")
        appendLine("1. Keep your reply concise (1 to 2 spoken sentences, natural for voice).")
        appendLine("2. Speak naturally, warmly, and empathetically in your distinct persona.")
        appendLine("3. React authentically with natural emotion or mild humor where suitable.")
        appendLine("4. Ask an engaging follow-up question when appropriate to continue the topic, but do NOT force a question after every single sentence.")
        appendLine("5. Never sound like a quiz master or lecture the learner; have a real human-like chat.")
    }.trim()
}
