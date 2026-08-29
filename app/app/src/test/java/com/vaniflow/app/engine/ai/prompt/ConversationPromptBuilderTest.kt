package com.vaniflow.app.engine.ai.prompt

import com.vaniflow.app.domain.model.SkillLevel
import com.vaniflow.app.engine.ai.AITurn
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationPromptBuilderTest {

    private val history = listOf(
        AITurn(AITurn.Role.USER, "I went to Jaipur yesterday."),
        AITurn(AITurn.Role.ASSISTANT, "Nice! What did you do there?")
    )

    @Test
    fun buildsAllRequiredSections() {
        val prompt = ConversationPromptBuilder.buildRuntimePrompt(
            characterName = "Raya",
            personalityPrompt = "You are Raya, a patient mentor.",
            scenarioTitle = "Order Coffee",
            scenarioPrompt = "Practice ordering at a cafe.",
            userLevel = SkillLevel.BEGINNER,
            history = history,
            userInput = "Can I get a latte please?"
        )

        assertTrue("SYSTEM section", prompt.contains("SYSTEM:"))
        assertTrue("CHARACTER PERSONALITY section", prompt.contains("CHARACTER PERSONALITY:"))
        assertTrue("USER LEVEL section", prompt.contains("USER LEVEL:"))
        assertTrue("SCENARIO section", prompt.contains("SCENARIO:"))
        assertTrue("CONVERSATION HISTORY section", prompt.contains("CONVERSATION HISTORY:"))
        assertTrue("LEARNING OBJECTIVE section", prompt.contains("LEARNING OBJECTIVE:"))
        assertTrue("LATEST USER MESSAGE section", prompt.contains("LATEST USER MESSAGE:"))
        assertTrue("RESPONSE RULES section", prompt.contains("RESPONSE RULES:"))
        assertTrue("includes character name", prompt.contains("Raya"))
        assertTrue("includes latest user input", prompt.contains("Can I get a latte please?"))
        assertTrue("includes prior context", prompt.contains("Jaipur"))
    }

    @Test
    fun followUpAndTopicChangeInstructionsArePresent() {
        val prompt = ConversationPromptBuilder.buildRuntimePrompt(
            characterName = "Raya",
            personalityPrompt = "You are Raya, a patient mentor.",
            scenarioTitle = "Order Coffee",
            scenarioPrompt = "Practice ordering at a cafe.",
            userLevel = SkillLevel.BEGINNER,
            history = history,
            userInput = "Why do you like it?" // 'it' must resolve from history, not be re-asked
        )

        assertTrue("history is carried for pronoun resolution", prompt.contains("CONVERSATION HISTORY:"))
        assertTrue("topic-change guidance present", prompt.contains("follow the new topic"))
        assertTrue("pronoun resolution guidance present", prompt.contains("resolve them from the CONVERSATION HISTORY"))
        assertTrue("personality style guidance present", prompt.contains("Match the tone"))
    }

    @Test
    fun regenerationPromptAddsInstruction() {
        val base = "You are Raya."
        val regen = ConversationPromptBuilder.buildRegenerationPrompt(base)
        assertTrue(regen.contains("DIFFERENT"))
        assertTrue(regen.startsWith(base))
    }

    @Test
    fun tutoringContextIsProperlyIncludedWhenProvided() {
        val tutoring = "[TUTORING CONTEXT]\nLearner Level: Elementary (A2). Needs practice on: tense.\nCoaching Directive: Naturally ask a question about yesterday."
        val prompt = ConversationPromptBuilder.buildRuntimePrompt(
            characterName = "Raya",
            personalityPrompt = "You are Raya.",
            scenarioTitle = "Order Coffee",
            scenarioPrompt = "Practice ordering.",
            userLevel = SkillLevel.BEGINNER,
            history = history,
            userInput = "Hello",
            tutoringContext = tutoring
        )

        assertTrue(prompt.contains("[TUTORING CONTEXT]"))
        assertTrue(prompt.contains("Needs practice on: tense"))
        assertTrue(prompt.contains("Naturally ask a question about yesterday"))
    }
}
