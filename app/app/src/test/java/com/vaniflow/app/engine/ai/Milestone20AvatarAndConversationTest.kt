package com.vaniflow.app.engine.ai

import com.vaniflow.app.domain.model.ConversationState
import com.vaniflow.app.domain.model.SkillLevel
import com.vaniflow.app.engine.ai.memory.ConversationMemoryManager
import com.vaniflow.app.engine.ai.prompt.ConversationPromptBuilder
import com.vaniflow.app.engine.character.CharacterRegistry
import com.vaniflow.app.ui.avatar.AvatarController
import com.vaniflow.app.ui.avatar.AvatarState
import com.vaniflow.app.ui.avatar.VisemeLipSyncController
import com.vaniflow.app.ui.avatar.model.AvatarEmotion
import com.vaniflow.app.ui.avatar.model.EmotionParser
import com.vaniflow.app.ui.avatar.toAvatarState
import com.vaniflow.app.ui.avatar.viseme.AmplitudeVisemeProvider
import com.vaniflow.app.ui.avatar.viseme.Viseme
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Comprehensive test suite for Milestone 20: Human Character, Realistic Talking Avatar & Natural Conversational AI.
 */
class Milestone20AvatarAndConversationTest {

    private lateinit var characterRegistry: CharacterRegistry
    private lateinit var memoryManager: ConversationMemoryManager
    private lateinit var avatarController: AvatarController
    private lateinit var lipSyncController: VisemeLipSyncController

    @Before
    fun setup() {
        characterRegistry = CharacterRegistry()
        memoryManager = ConversationMemoryManager()
        avatarController = AvatarController()
        lipSyncController = VisemeLipSyncController()
    }

    @Test
    fun testFourCharactersHaveDistinctPersonalitiesAndTiers() {
        val raya = characterRegistry.getCharacter("raya")
        val rudra = characterRegistry.getCharacter("rudra")
        val adwaita = characterRegistry.getCharacter("adwaita")
        val shub = characterRegistry.getCharacter("shub")

        assertEquals("Raya", raya.name)
        assertTrue(raya.systemPrompt.contains("warm") || raya.systemPrompt.contains("playful"))
        assertEquals(SkillLevel.BEGINNER, raya.level)

        assertEquals("Rudra", rudra.name)
        assertTrue(rudra.systemPrompt.contains("energetic") || rudra.systemPrompt.contains("enthusiastic"))
        assertEquals(SkillLevel.INTERMEDIATE, rudra.level)

        assertEquals("Adwaita", adwaita.name)
        assertTrue(adwaita.systemPrompt.contains("executive") || adwaita.systemPrompt.contains("sophisticated"))
        assertEquals(SkillLevel.ADVANCED, adwaita.level)

        assertEquals("Shub", shub.name)
        assertTrue(shub.systemPrompt.contains("analytical") || shub.systemPrompt.contains("structured"))
        assertEquals(SkillLevel.ADVANCED, shub.level)
    }

    @Test
    fun testConversationPromptDoesNotForceQuestionsEveryTime() {
        val raya = characterRegistry.getCharacter("raya")
        val prompt = ConversationPromptBuilder.buildRuntimePrompt(
            characterName = raya.name,
            personalityPrompt = raya.systemPrompt,
            scenarioTitle = "Casual Chat",
            scenarioPrompt = "Talking about daily life",
            userLevel = raya.level,
            history = emptyList(),
            userInput = "I love traveling to the mountains."
        )

        assertTrue(prompt.contains("RESPONSE RULES:"))
        assertTrue("Must NOT mandate ending with a question every single time", prompt.contains("ONLY when appropriate"))
        assertTrue("Must support natural conversational reactions", prompt.contains("Start with a natural reaction"))
    }

    @Test
    fun testEmotionParserInfersAppropriateEmotions() {
        val happyTurn = EmotionParser.parseFromText("Honestly, I love spicy Indian food 😄. What about you?")
        assertEquals(AvatarEmotion.HAPPY, happyTurn.emotion)

        val excitedTurn = EmotionParser.parseFromText("That is awesome! 🎉 Tell me more!")
        assertEquals(AvatarEmotion.EXCITED, excitedTurn.emotion)

        val curiousTurn = EmotionParser.parseFromText("What made you choose that particular college?")
        assertEquals(AvatarEmotion.CURIOUS, curiousTurn.emotion)

        val thoughtfulTurn = EmotionParser.parseFromText("Hmm, let me think about how to phrase that better...")
        assertEquals(AvatarEmotion.THOUGHTFUL, thoughtfulTurn.emotion)

        val encouragingTurn = EmotionParser.parseFromText("Don't worry, you're doing great! Keep going.")
        assertEquals(AvatarEmotion.ENCOURAGING, encouragingTurn.emotion)

        val neutralTurn = EmotionParser.parseFromText("I see what you mean.")
        assertEquals(AvatarEmotion.NEUTRAL, neutralTurn.emotion)
    }

    @Test
    fun testAvatarStateTransitions() {
        avatarController.update(ConversationState.IDLE)
        assertEquals(AvatarState.IDLE, avatarController.avatarState.value)

        avatarController.update(ConversationState.LISTENING)
        assertEquals(AvatarState.LISTENING, avatarController.avatarState.value)

        avatarController.update(ConversationState.THINKING)
        assertEquals(AvatarState.THINKING, avatarController.avatarState.value)

        avatarController.update(ConversationState.AI_SPEAKING, AvatarEmotion.HAPPY)
        assertEquals(AvatarState.HAPPY, avatarController.avatarState.value)

        avatarController.update(ConversationState.INTERRUPTED)
        assertEquals(AvatarState.INTERRUPTED, avatarController.avatarState.value)
    }

    @Test
    fun testLipSyncAmplitudeTrackingAndInterruption() {
        lipSyncController.onSpeechStart()
        assertTrue("Mouth openness must start open on speech start", lipSyncController.mouthOpennessFlow.value >= 0.20f)

        lipSyncController.onAmplitude(0.8f)
        assertTrue("Mouth openness must be positive during speech", lipSyncController.mouthOpennessFlow.value > 0.1f)

        // Instant interruption snaps mouth shut
        lipSyncController.interrupt()
        assertEquals(0.0f, lipSyncController.mouthOpennessFlow.value, 0.001f)
        assertEquals(Viseme.REST, lipSyncController.currentViseme().viseme)
    }

    @Test
    fun testAmplitudeVisemeProviderSmoothing() {
        val provider = AmplitudeVisemeProvider()
        provider.onSpeechStart()
        provider.onAudioFrame(0.9f)
        val visemeState = provider.getCurrentViseme()
        assertTrue("High amplitude must produce an open viseme", visemeState.viseme == Viseme.A || visemeState.viseme == Viseme.O || visemeState.viseme == Viseme.E)
        assertTrue(visemeState.intensity > 0.3f)

        provider.onSpeechEnd()
        assertEquals(Viseme.REST, provider.getCurrentViseme().viseme)
    }

    @Test
    fun testConversationMemoryExtractsUserFactsAndTopic() {
        memoryManager.addTurn("user", "My name is Rahul")
        memoryManager.addTurn("assistant", "Nice to meet you Rahul! What do you like to do?")
        memoryManager.addTurn("user", "I love playing cricket on weekends")

        assertEquals("Hobbies & Free Time", memoryManager.getCurrentTopic())
        val facts = memoryManager.getKeyFacts()
        assertTrue("Must extract user's name", facts.any { it.contains("Rahul") })
        assertTrue("Must extract user's preference", facts.any { it.contains("cricket") || it.contains("preference") })

        val prompt = memoryManager.buildMemoryPrompt("Base Prompt")
        assertTrue("Memory prompt must contain user facts", prompt.contains("Rahul"))
        assertTrue("Memory tokens must be bounded", memoryManager.estimateMemoryTokens() < 500)
    }

    @Test
    fun testPronounResolutionContextInPrompt() {
        memoryManager.addTurn("user", "I went to Jaipur yesterday.")
        memoryManager.addTurn("assistant", "Nice! Jaipur is beautiful. Where did you go there?")
        memoryManager.addTurn("user", "With my friends.")

        val prompt = memoryManager.buildMemoryPrompt("You are Raya")
        assertTrue(prompt.contains("Jaipur"))
        assertEquals("Travel & Exploration", memoryManager.getCurrentTopic())
    }
}