package com.vaniflow.app

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.vaniflow.app.engine.ai.AIResult
import com.vaniflow.app.engine.ai.FallbackAIEngine
import com.vaniflow.app.engine.character.CharacterRegistry
import com.vaniflow.app.engine.scenario.ScenarioRegistry
import com.vaniflow.app.engine.tts.SentenceSplitter
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

/**
 * Milestone 13 - Conversation Flow Instrumented Tests.
 * Runs on the connected physical device (CASSE65LH66SWSZP).
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class ConversationFlowInstrumentedTest {

    @get:Rule val hiltRule = HiltAndroidRule(this)

    @Inject lateinit var characterRegistry: CharacterRegistry
    @Inject lateinit var scenarioRegistry: ScenarioRegistry
    @Inject lateinit var fallbackAIEngine: FallbackAIEngine

    @Before fun setup() { hiltRule.inject() }

    @Test
    fun allFourCharactersLoadOnDevice() {
        val ids = characterRegistry.characters.map { it.id }.toSet()
        assertEquals(4, characterRegistry.characters.size)
        assertTrue(ids.containsAll(listOf("raya","rudra","adwaita","shub")))
    }

    @Test
    fun rayaAndAdwaitaHaveFemaleVoiceIds() {
        assertTrue(characterRegistry.getCharacter("raya")!!.voiceId.contains("female", ignoreCase = true))
        assertTrue(characterRegistry.getCharacter("adwaita")!!.voiceId.contains("female", ignoreCase = true))
    }

    @Test
    fun rudraAndShubHaveMaleVoiceIds() {
        assertTrue(characterRegistry.getCharacter("rudra")!!.voiceId.endsWith("_male", ignoreCase = true))
        assertTrue(characterRegistry.getCharacter("shub")!!.voiceId.endsWith("_male", ignoreCase = true))
    }

    @Test
    fun allSixCoreScenariosLoadOnDevice() {
        listOf("order_coffee","airport_checkin","job_interview","project_standup","meeting_someone","workplace_discussion").forEach { id ->
            val s = scenarioRegistry.getScenario(id)
            assertNotNull("Scenario '$id' must exist on device", s)
            assertTrue(s!!.initialMessage.isNotBlank())
        }
    }

    @Test
    fun fallbackAIProducesDistinctAnswersForDifferentQuestions() = runBlocking {
        val prompt = "You are Raya, an English conversation partner."
        val questions = listOf("Tell me about food","How was your day","Tell me about travel","Tell me about work","Hobbies?")
        val responses = questions.map { q ->
            (fallbackAIEngine.generateResponse(prompt, emptyList(), q) as? AIResult.Success)?.text ?: ""
        }
        assertTrue("Must produce >= 3 distinct responses. Got ${responses.toSet().size}", responses.toSet().size >= 3)
    }

    @Test
    fun fallbackAIResponsesAreNonBlank() = runBlocking {
        val prompt = "You are Shub."
        listOf("Hello","What is your name?","Tell me about yourself").forEach { input ->
            val result = fallbackAIEngine.generateResponse(prompt, emptyList(), input)
            assertTrue(result is AIResult.Success)
            assertTrue((result as AIResult.Success).text.isNotBlank())
        }
    }

    @Test
    fun sentenceSplitterWorksCorrectlyOnDevice() {
        val sentences = SentenceSplitter.splitIntoSentences("Welcome! How are you today? Let us practice English together.")
        assertEquals(3, sentences.size)
        assertTrue(sentences[0].contains("Welcome"))
    }
}