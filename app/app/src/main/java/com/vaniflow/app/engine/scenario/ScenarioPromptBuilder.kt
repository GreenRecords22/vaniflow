package com.vaniflow.app.engine.scenario

import com.vaniflow.app.domain.model.Scenario

/**
 * Builds structured environmental context for practice scenarios.
 */
object ScenarioPromptBuilder {

    fun buildScenarioPrompt(scenario: Scenario): String = buildString {
        appendLine("Active Situation: ${scenario.title}")
        appendLine("Description: ${scenario.description}")
        appendLine("Context & Role: ${scenario.systemPrompt}")
        if (scenario.targetVocabulary.isNotEmpty()) {
            appendLine("Recommended Vocabulary: ${scenario.targetVocabulary.joinToString(", ")}")
        }
        appendLine("Directive: Immerse the user in this exact situation. Stay in character.")
    }.trim()
}
