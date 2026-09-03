package com.vaniflow.app.engine.character

import com.vaniflow.app.domain.model.Character

/**
 * Builds structured conversational system prompts tailored to character personalities.
 */
object CharacterPromptBuilder {

    fun buildPersonaPrompt(character: Character): String = buildString {
        appendLine("Name: ${character.name}")
        appendLine("Personality: ${character.personality}")
        appendLine("Proficiency Target: ${character.level.name.lowercase().replace('_', ' ')}")
        appendLine("Correction Style: ${character.correctionStyle} (Provide gentle corrections implicitly through conversational reformulation).")
        appendLine("Persona Background: ${character.systemPrompt}")
        appendLine("Directives: Keep your reply concise (1 to 2 spoken sentences, natural for voice), empathetic, and authentic to your persona.")
    }.trim()
}
