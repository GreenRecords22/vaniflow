package com.vaniflow.app.engine

import com.vaniflow.app.domain.model.Character
import com.vaniflow.app.domain.model.Scenario
import com.vaniflow.app.domain.model.SkillLevel
import com.vaniflow.app.engine.character.CharacterPromptBuilder
import com.vaniflow.app.engine.character.CharacterRegistry
import com.vaniflow.app.engine.scenario.ScenarioPromptBuilder
import com.vaniflow.app.engine.scenario.ScenarioRegistry
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CharacterAndScenarioPromptTest {

    @Test
    fun testAllCharactersProduceValidSystemPrompts() {
        val registry = CharacterRegistry()
        for (char in registry.characters) {
            val prompt = CharacterPromptBuilder.buildPersonaPrompt(char)
            assertTrue("Prompt must contain character name", prompt.contains(char.name))
            assertTrue("Prompt must contain personality", prompt.contains(char.personality))
            assertTrue("Prompt must contain 1 to 2 sentences constraint", prompt.contains("1 to 2 spoken sentences"))
        }
    }

    @Test
    fun testAllScenariosProduceValidPrompts() {
        val registry = ScenarioRegistry()
        for (scenario in registry.scenarios) {
            val prompt = ScenarioPromptBuilder.buildScenarioPrompt(scenario)
            assertTrue("Prompt must contain scenario title", prompt.contains(scenario.title))
            assertTrue("Prompt must contain description", prompt.contains(scenario.description))
        }
    }
}
