package com.vaniflow.app.engine

import com.vaniflow.app.engine.character.CharacterRegistry
import com.vaniflow.app.engine.scenario.ScenarioRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RegistryTest {

    private val characterRegistry = CharacterRegistry()
    private val scenarioRegistry = ScenarioRegistry()

    @Test
    fun testAllMvpCharactersPresent() {
        val characters = characterRegistry.characters
        assertEquals(4, characters.size)

        val names = characters.map { it.name }
        assertTrue(names.contains("Raya"))
        assertTrue(names.contains("Rudra"))
        assertTrue(names.contains("Adwaita"))
        assertTrue(names.contains("Shub"))
    }

    @Test
    fun testCharacterRetrieval() {
        val raya = characterRegistry.getCharacter("raya")
        assertNotNull(raya)
        assertEquals("Raya", raya.name)
        assertTrue(raya.isDefault)
    }

    @Test
    fun testAllMvpScenariosPresent() {
        val scenarios = scenarioRegistry.scenarios
        assertEquals(7, scenarios.size)

        val ids = scenarios.map { it.id }
        assertTrue(ids.contains("free_conversation"))
        assertTrue(ids.contains("order_coffee"))
        assertTrue(ids.contains("airport_checkin"))
        assertTrue(ids.contains("job_interview"))
        assertTrue(ids.contains("project_standup"))
        assertTrue(ids.contains("meeting_someone"))
        assertTrue(ids.contains("workplace_discussion"))
    }

    @Test
    fun testScenarioRetrieval() {
        val coffee = scenarioRegistry.getScenario("order_coffee")
        assertNotNull(coffee)
        assertEquals("Order Coffee", coffee.title)
        assertTrue(coffee.targetVocabulary.isNotEmpty())
    }
}
