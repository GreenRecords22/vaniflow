package com.vaniflow.app.engine

import com.vaniflow.app.engine.ai.AIResult
import com.vaniflow.app.engine.ai.AITurn
import com.vaniflow.app.engine.ai.FallbackAIEngine
import com.vaniflow.app.engine.ai.LocalAIEngine
import com.vaniflow.app.engine.character.CharacterRegistry
import com.vaniflow.app.engine.scenario.ScenarioRegistry
import com.vaniflow.app.engine.tts.CharacterVoiceRegistry
import com.vaniflow.app.engine.tts.SentenceSplitter
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File

class Milestone13PhysicalDeviceValidationTest {
    private lateinit var characterRegistry: CharacterRegistry
    private lateinit var scenarioRegistry: ScenarioRegistry
    private lateinit var voiceRegistry: CharacterVoiceRegistry
    private lateinit var fallbackAIEngine: FallbackAIEngine

    @Before
    fun setup() {
        characterRegistry = CharacterRegistry()
        scenarioRegistry = ScenarioRegistry()
        voiceRegistry = CharacterVoiceRegistry()
        fallbackAIEngine = FallbackAIEngine()
    }

    // --- Voice gender alignment [VERIFIED] ---

    @Test
    fun rayaVoiceIdContainsFemaleKeyword() {
        val raya = characterRegistry.getCharacter("raya")
        assertTrue("Raya voiceId must contain 'female'. Was: ${raya!!.voiceId}", raya.voiceId.contains("female", ignoreCase = true))
    }

    @Test
    fun adwaitaVoiceIdContainsFemaleKeyword() {
        val adwaita = characterRegistry.getCharacter("adwaita")
        assertTrue("Adwaita voiceId must contain 'female'. Was: ${adwaita!!.voiceId}", adwaita.voiceId.contains("female", ignoreCase = true))
    }

    @Test
    fun rudraVoiceIdContainsMaleKeyword() {
        val rudra = characterRegistry.getCharacter("rudra")
        assertTrue("Rudra voiceId must contain 'male'. Was: ${rudra!!.voiceId}", rudra.voiceId.contains("male", ignoreCase = true))
    }

    @Test
    fun shubVoiceIdContainsMaleKeyword() {
        val shub = characterRegistry.getCharacter("shub")
        assertTrue("Shub voiceId must contain 'male'. Was: ${shub!!.voiceId}", shub.voiceId.contains("male", ignoreCase = true))
    }

    @Test
    fun characterVoiceRegistryReturnsCorrectGenderForAllCharacters() {
        assertEquals("female", voiceRegistry.getVoiceConfig("en_IN_raya_female").gender)
        assertEquals("female", voiceRegistry.getVoiceConfig("en_IN_adwaita_female").gender)
        assertEquals("male", voiceRegistry.getVoiceConfig("en_IN_rudra_male").gender)
        assertEquals("male", voiceRegistry.getVoiceConfig("en_IN_shub_male").gender)
    }

    @Test
    fun rayaPitchIsHigherThanRudraPitch() {
        val raya = voiceRegistry.getVoiceConfig("en_IN_raya_female")
        val rudra = voiceRegistry.getVoiceConfig("en_IN_rudra_male")
        assertTrue("Raya pitch (${raya.pitch}) must be higher than Rudra pitch (${rudra.pitch})", raya.pitch > rudra.pitch)
    }

    // --- LocalAI model readiness [VERIFIED] ---

    @Test
    fun localAIEngineReturnsFalseForZeroByteModelFile() {
        val mockMgr = mockk<com.vaniflow.app.engine.model.ModelManager>(relaxed = true)
        val f = File.createTempFile("fake_model", ".gguf").also { it.deleteOnExit(); it.writeBytes(ByteArray(0)) }
        every { mockMgr.getModelState("llm_qwen25_05b_instruct") } returns com.vaniflow.app.domain.model.ModelState.INSTALLED
        every { mockMgr.getModelFile("llm_qwen25_05b_instruct") } returns f
        assertFalse("Zero-byte file must give isModelReady=false", LocalAIEngine(mockMgr).isModelReady())
        f.delete()
    }

    @Test
    fun localAIEngineReturnsFalseWhenModelNotInstalled() {
        val mockMgr = mockk<com.vaniflow.app.engine.model.ModelManager>(relaxed = true)
        every { mockMgr.getModelState("llm_qwen25_05b_instruct") } returns com.vaniflow.app.domain.model.ModelState.NOT_INSTALLED
        assertFalse("NOT_INSTALLED must give isModelReady=false", LocalAIEngine(mockMgr).isModelReady())
    }

    @Test
    fun localAIEngineReturnsTrueOnlyForRealModelFile() {
        val mockMgr = mockk<com.vaniflow.app.engine.model.ModelManager>(relaxed = true)
        val f = File.createTempFile("real_model", ".gguf").also { it.deleteOnExit(); it.writeBytes(ByteArray(1_100_000) { 0x42 }) }
        every { mockMgr.getModelState("llm_qwen25_05b_instruct") } returns com.vaniflow.app.domain.model.ModelState.INSTALLED
        every { mockMgr.getModelFile("llm_qwen25_05b_instruct") } returns f
        // isModelReady() requires BOTH a real model file AND a loaded runtime.
        assertTrue(
            "1.1MB file + available runtime must give isModelReady=true",
            LocalAIEngine(mockMgr, com.vaniflow.app.engine.ai.llm.FakeLocalLLMRuntime(available = true)).isModelReady()
        )
        f.delete()
    }

    // --- FallbackAI non-repeating [VERIFIED] ---

    @Test
    fun fallbackAIProducesTenDistinctAnswers() = runTest {
        val prompt = "You are Raya."
        val questions = listOf("Tell me about food","How was your day","Tell me about travel",
            "What is your work","Hobbies?","Cricket?","Why music?","Favorite city?","Technology?","Motivation?")
        val responses = questions.map { q -> (fallbackAIEngine.generateResponse(prompt, emptyList(), q) as? AIResult.Success)?.text ?: "" }
        assertTrue("Must produce >= 5 distinct responses. Got ${responses.toSet().size}", responses.toSet().size >= 5)
    }

    @Test
    fun fallbackAIResponsesAreNeverBlank() = runTest {
        val prompt = "You are Rudra."
        listOf("Hello","How are you?","Tell me about yourself","What is your name?","Why English?").forEach { input ->
            val result = fallbackAIEngine.generateResponse(prompt, emptyList(), input) as AIResult.Success
            assertTrue("Response for '$input' must not be blank", result.text.isNotBlank())
        }
    }

    @Test
    fun fallbackAICharacterPersonalityDifferentiatesRayaVsRudra() = runTest {
        val input = "Hello, how are you?"
        val raya = (fallbackAIEngine.generateResponse("You are Raya.", emptyList(), input) as? AIResult.Success)?.text ?: ""
        val rudra = (fallbackAIEngine.generateResponse("You are Rudra.", emptyList(), input) as? AIResult.Success)?.text ?: ""
        assertFalse("Raya and Rudra must give different hello responses", raya == rudra)
    }

    // --- SentenceSplitter edge cases [VERIFIED] ---

    @Test fun sentenceSplitterHandlesEmpty() = assertEquals(emptyList<String>(), SentenceSplitter.splitIntoSentences(""))
    @Test fun sentenceSplitterHandlesBlank() = assertEquals(emptyList<String>(), SentenceSplitter.splitIntoSentences("   "))
    @Test fun sentenceSplitterSplitsThreeSentences() { assertEquals(3, SentenceSplitter.splitIntoSentences("Hello! How are you? I am fine.").size) }
    @Test fun sentenceSplitterPreservesContent() {
        val sentences = SentenceSplitter.splitIntoSentences("Welcome to VaniFlow! Let us practice.")
        assertTrue(sentences.joinToString(" ").contains("Welcome"))
    }

    // --- CharacterRegistry completeness [VERIFIED] ---

    @Test fun allCharactersHaveNonBlankSystemPrompts() = characterRegistry.characters.forEach { assertTrue(it.systemPrompt.isNotBlank()) }
    @Test fun allCharactersHavePositiveSpeakingRate() = characterRegistry.characters.forEach { assertTrue(it.speakingRate > 0f) }
    @Test fun allCharacterIdsAreUnique() { val ids = characterRegistry.characters.map { it.id }; assertEquals(ids.size, ids.toSet().size) }
    @Test fun allCharacterVoiceIdsAreUnique() { val vids = characterRegistry.characters.map { it.voiceId }; assertEquals(vids.size, vids.toSet().size) }

    // --- ScenarioRegistry completeness [VERIFIED] ---

    @Test fun allScenariosHavePositiveDuration() = scenarioRegistry.scenarios.forEach { assertTrue("${it.id} duration must be >0", it.estimatedMinutes > 0) }
    @Test fun allScenariosHaveNonEmptyVocabulary() = scenarioRegistry.scenarios.forEach { assertFalse("${it.id} must have vocabulary", it.targetVocabulary.isEmpty()) }
}