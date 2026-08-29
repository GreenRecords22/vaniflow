package com.vaniflow.app.engine

import com.vaniflow.app.domain.model.ConversationTurn
import com.vaniflow.app.domain.model.Correction
import com.vaniflow.app.domain.model.CorrectionCategory
import com.vaniflow.app.domain.model.FeedbackImportance
import com.vaniflow.app.domain.model.SkillLevel
import com.vaniflow.app.engine.ai.AIRoutingLevel
import com.vaniflow.app.engine.ai.AIResult
import com.vaniflow.app.engine.ai.DefaultCloudAIProvider
import com.vaniflow.app.engine.ai.FallbackAIEngine
import com.vaniflow.app.engine.ai.LocalAIEngine
import com.vaniflow.app.engine.ai.SmartAIRouter
import com.vaniflow.app.engine.ai.cache.AIResponseCache
import com.vaniflow.app.engine.character.CharacterRegistry
import com.vaniflow.app.engine.learning.DefaultFeedbackEngine
import com.vaniflow.app.engine.learning.GrammarEngine
import com.vaniflow.app.engine.learning.PronunciationScorer
import com.vaniflow.app.engine.learning.VocabularyEngine
import com.vaniflow.app.engine.scenario.ScenarioRegistry
import com.vaniflow.app.engine.tts.SentenceSplitter
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class Milestone12ProductionQATest {

    private lateinit var characterRegistry: CharacterRegistry
    private lateinit var scenarioRegistry: ScenarioRegistry
    private lateinit var grammarEngine: GrammarEngine
    private lateinit var vocabularyEngine: VocabularyEngine
    private lateinit var pronunciationScorer: PronunciationScorer
    private lateinit var feedbackEngine: DefaultFeedbackEngine

    @Before
    fun setup() {
        characterRegistry = CharacterRegistry()
        scenarioRegistry = ScenarioRegistry()
        grammarEngine = GrammarEngine()
        vocabularyEngine = VocabularyEngine()
        pronunciationScorer = PronunciationScorer()
        feedbackEngine = DefaultFeedbackEngine(grammarEngine)
    }

    @Test
    fun testAllFourCharactersHaveUniqueProfilesAndVoices() {
        val characters = characterRegistry.characters
        assertEquals(4, characters.size)

        val ids = characters.map { it.id }.toSet()
        assertEquals(4, ids.size)

        val raya = characterRegistry.getCharacter("raya")
        val rudra = characterRegistry.getCharacter("rudra")
        val adwaita = characterRegistry.getCharacter("adwaita")
        val shub = characterRegistry.getCharacter("shub")

        assertNotNull(raya)
        assertNotNull(rudra)
        assertNotNull(adwaita)
        assertNotNull(shub)

        // Verify distinct personalities & levels
        assertEquals(SkillLevel.BEGINNER, raya!!.level)
        assertEquals(SkillLevel.INTERMEDIATE, rudra!!.level)
        assertEquals(SkillLevel.ADVANCED, adwaita!!.level)
        assertEquals(SkillLevel.ADVANCED, shub!!.level)

        // Verify prompt specialization
        assertTrue(raya.systemPrompt.contains("Raya"))
        assertTrue(rudra.systemPrompt.contains("Rudra"))
        assertTrue(adwaita.systemPrompt.contains("Adwaita"))
        assertTrue(shub.systemPrompt.contains("Shub"))
    }

    @Test
    fun testAllSixScenariosHaveValidRolesAndInitialMessages() {
        val scenarios = scenarioRegistry.scenarios
        assertEquals(7, scenarios.size)

        val expectedIds = listOf(
            "free_conversation",
            "order_coffee",
            "airport_checkin",
            "job_interview",
            "project_standup",
            "meeting_someone",
            "workplace_discussion"
        )

        expectedIds.forEach { id ->
            val scenario = scenarioRegistry.getScenario(id)
            assertNotNull("Scenario $id must exist", scenario)
            assertTrue("Scenario $id must have initial message", scenario!!.initialMessage.isNotBlank())
            assertTrue("Scenario $id must have system prompt", scenario.systemPrompt.isNotBlank())
            assertTrue("Scenario $id must have positive duration", scenario.estimatedMinutes > 0)
        }
    }

    @Test
    fun testSentenceSplitterStreamingEfficiency() {
        val text = "Welcome to the coffee shop! How can I help you today? We have fresh pastries."

        val sentences = SentenceSplitter.splitIntoSentences(text)
        assertEquals(3, sentences.size)
        assertEquals("Welcome to the coffee shop!", sentences[0])
        assertEquals("How can I help you today?", sentences[1])
        assertEquals("We have fresh pastries.", sentences[2])
    }

    @Test
    fun testEndToEndFeedbackPipelineIndianEnglishCorrection() = runTest {
        val userSpeech = "I am having two brothers."
        val correction = feedbackEngine.analyzeUtterance(userSpeech)

        assertNotNull(correction)
        assertEquals(CorrectionCategory.NATURAL_PHRASING, correction!!.category)
        assertEquals("I have two brothers.", correction.suggestedText)
    }

    @Test
    fun testScorerEvaluatesMultidimensionalSkills() {
        val turns = listOf(
            ConversationTurn(
                id = "1",
                sessionId = "s1",
                speaker = ConversationTurn.Speaker.AI,
                text = "Hello! What can I get for you?",
                timestamp = 1000L
            ),
            ConversationTurn(
                id = "2",
                sessionId = "s1",
                speaker = ConversationTurn.Speaker.USER,
                text = "I am living here since three years.",
                timestamp = 2000L,
                correction = Correction(
                    originalText = "I am living here since three years.",
                    suggestedText = "I have been living here for three years.",
                    explanation = "Use 'for' with duration.",
                    category = CorrectionCategory.GRAMMAR
                )
            ),
            ConversationTurn(
                id = "3",
                sessionId = "s1",
                speaker = ConversationTurn.Speaker.USER,
                text = "I would like to order one hot cappuccino please.",
                timestamp = 3000L
            )
        )

        val score = pronunciationScorer.calculateSessionScore(turns, speakingTimeMinutes = 5)

        assertEquals(5, score.speakingTimeMinutes)
        assertTrue(score.fluencyScore in 50..100)
        assertTrue(score.grammarScore in 50..100)
        assertTrue(score.pronunciationScore in 50..100)
        assertTrue(score.vocabularyScore in 50..100)
        assertTrue(score.strongestArea.isNotBlank())
        assertTrue(score.focusNext.isNotBlank())
    }

    @Test
    fun testSmartAIRouterFullOfflineFallbackGuarantee() = runTest {
        val fallback = FallbackAIEngine()
        val local = LocalAIEngine(mockk(relaxed = true))
        val cloud = DefaultCloudAIProvider()
        val cache = mockk<AIResponseCache>(relaxed = true)
        coEvery { cache.getCachedResponse(any(), any(), any(), any(), any(), any()) } returns null

        val router = SmartAIRouter(local, cloud, fallback, cache)

        val result = router.generateResponse(
            systemPrompt = "You are Rudra",
            conversationHistory = emptyList(),
            userInput = "Let's start the project standup."
        )

        assertTrue(result is AIResult.Success)
        val success = result as AIResult.Success
        assertTrue(success.text.isNotBlank())
    }
}
