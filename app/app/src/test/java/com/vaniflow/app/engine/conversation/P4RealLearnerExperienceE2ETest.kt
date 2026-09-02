package com.vaniflow.app.engine.conversation

import com.vaniflow.app.domain.model.Character
import com.vaniflow.app.domain.model.ConversationTurn
import com.vaniflow.app.domain.model.Scenario
import com.vaniflow.app.domain.model.SkillLevel
import com.vaniflow.app.engine.ai.AIEngine
import com.vaniflow.app.engine.learning.FeedbackEngine
import com.vaniflow.app.engine.learning.tutor.EnglishCorrectionEngine
import com.vaniflow.app.engine.learning.tutor.LearningMemoryManager
import com.vaniflow.app.engine.learning.tutor.model.TutorAction
import com.vaniflow.app.engine.speech.model.SpeechAudioSegment
import com.vaniflow.app.engine.tts.TTSEngine
import com.vaniflow.app.engine.tts.TTSResult
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Production validation test simulating the complete Phase 4 learner experience:
 * User: Sunil
 * Scenario: Job Interview
 * Character: Raya
 *
 * Sequence:
 * 1. Intro: "My name is Sunil and I am working in animal science department."
 * 2. Grammar mistake: "Yesterday I buyed vegetables."
 * 3. Retry success: "Yesterday I bought vegetables."
 * 4. Clean sentence: "I enjoy conducting field research with my team."
 * 5. Hesitation utterance: Simulated slow/hesitant speech.
 * 6. Pronunciation candidate: "I am very thoughtful about research methodology."
 * 7. Clean concluding sentence: "Thank you for considering my application."
 */
@OptIn(ExperimentalCoroutinesApi::class)
class P4RealLearnerExperienceE2ETest {

    private lateinit var aiEngine: AIEngine
    private lateinit var ttsEngine: TTSEngine
    private lateinit var feedbackEngine: FeedbackEngine
    private lateinit var correctionEngine: EnglishCorrectionEngine
    private lateinit var learningMemoryManager: LearningMemoryManager
    private lateinit var engine: ConversationEngine
    private lateinit var dispatcher: TestDispatcher

    private val rayaCharacter = Character(
        id = "raya",
        name = "Raya",
        personality = "warm and encouraging AI tutor",
        level = SkillLevel.INTERMEDIATE,
        voiceId = "en_IN_raya_female",
        speakingRate = 1.0f,
        correctionStyle = "supportive",
        avatarUrl = null,
        systemPrompt = "You are Raya, a supportive English tutor."
    )

    private val jobInterviewScenario = Scenario(
        id = "job_interview",
        title = "Job Interview",
        description = "Practice interviewing for a professional role",
        category = "Career",
        difficulty = SkillLevel.INTERMEDIATE,
        estimatedMinutes = 5,
        initialMessage = "Hello Alex, welcome to the interview! Could you please introduce yourself?",
        systemPrompt = "Conduct a professional yet encouraging interview.",
        targetVocabulary = listOf("methodology", "department", "experience")
    )

    @Before
    fun setup() {
        dispatcher = StandardTestDispatcher()
        Dispatchers.setMain(dispatcher)

        aiEngine = mockk(relaxed = true)
        ttsEngine = mockk(relaxed = true)
        feedbackEngine = mockk(relaxed = true)
        correctionEngine = EnglishCorrectionEngine()
        learningMemoryManager = LearningMemoryManager()

        coEvery { ttsEngine.speak(any(), any(), any()) } returns TTSResult.Completed(0)
        coEvery { ttsEngine.stop() } returns Unit
        coEvery { feedbackEngine.analyzeUtterance(any()) } returns null
        every { aiEngine.streamResponse(any(), any(), any()) } returns flowOf("Thank you for sharing that with me.")

        engine = ConversationEngine(
            aiEngine = aiEngine,
            ttsEngine = ttsEngine,
            feedbackEngine = feedbackEngine,
            correctionEngine = correctionEngine,
            learningMemoryManager = learningMemoryManager
        )

        engine.startSession(rayaCharacter, jobInterviewScenario)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testCompleteSunilJobInterviewSession() = runTest {
        // Step 0: Greeting verified
        assertEquals(1, engine.turns.value.size)
        assertTrue(engine.turns.value.first().text.contains("welcome to the interview", ignoreCase = true))

        // Step 1: Sunil introduces himself
        // "My name is Sunil and I am working in animal science department."
        engine.processUserUtterance("My name is Sunil and I am working in animal science department.")

        assertEquals(ConversationEngine.TutorState.NORMAL, engine.tutorState)
        val turnsAfterTurn1 = engine.turns.value
        val turn1 = turnsAfterTurn1.find { it.speaker == ConversationTurn.Speaker.USER && it.text.contains("Sunil") }
        assertNotNull(turn1)

        // Step 2: Sunil makes an irregular past tense error
        // "Yesterday I buyed vegetables." -> Triggers ASK_RETRY & WAITING_FOR_RETRY
        engine.processUserUtterance("Yesterday I buyed vegetables.")

        assertEquals(ConversationEngine.TutorState.WAITING_FOR_RETRY, engine.tutorState)
        assertNotNull(engine.activeRetry)
        assertEquals("past_buyed", engine.activeRetry?.originalError?.ruleIdentifier)
        val initialMastery = learningMemoryManager.masteryMap["past_buyed"]
        assertNotNull(initialMastery)
        assertTrue(initialMastery!!.masteryScore < 50)

        // Step 3: Sunil executes a successful retry
        // "Yesterday I bought vegetables." -> Triggers PRAISE_SUCCESS and returns to NORMAL
        engine.processUserUtterance("Yesterday I bought vegetables.")

        assertEquals(ConversationEngine.TutorState.NORMAL, engine.tutorState)
        assertNull(engine.activeRetry)
        val updatedMastery = learningMemoryManager.masteryMap["past_buyed"]
        assertNotNull(updatedMastery)
        assertTrue("Mastery should improve after successful retry", updatedMastery!!.masteryScore > initialMastery.masteryScore)

        // Step 4: Clean sentence
        engine.processUserUtterance("I enjoy conducting field research with my team.")
        assertEquals(ConversationEngine.TutorState.NORMAL, engine.tutorState)

        // Step 5: Utterance with simulated speech audio
        val audioSamples = ShortArray(32000) { 1000 } // simulated audible speech
        val segment = SpeechAudioSegment(samples = audioSamples, durationMs = 2000L)
        engine.processUserUtterance("I think teamwork is very important.", audioSegment = segment)
        assertEquals(ConversationEngine.TutorState.NORMAL, engine.tutorState)

        // Step 6: Pronunciation practice candidate
        engine.processUserUtterance("I am very thoughtful about research methodology.")
        assertEquals(ConversationEngine.TutorState.NORMAL, engine.tutorState)

        // Step 7: Final clean concluding response
        engine.processUserUtterance("Thank you for considering my application today.")
        assertEquals(ConversationEngine.TutorState.NORMAL, engine.tutorState)

        // Step 8: End Session & verify SessionScore and Summary
        val score = engine.endSession()
        assertNotNull(score)
        assertTrue("Speaking time should be at least 1 minute", score.speakingTimeMinutes >= 1)
        assertTrue("Fluency score should be positive and bounded", score.fluencyScore in 50..100)
        assertTrue("Grammar score should be positive and bounded", score.grammarScore in 50..100)
        assertNotNull(score.strongestArea)
        assertNotNull(score.focusNext)

        val summary = engine.getSessionSummary()
        assertTrue("User turns count should reflect user utterances", summary.userTurnsCount >= 5)
        assertEquals(1, summary.successfulRetriesCount)
        assertTrue("Learner profile should have recorded successful retries", engine.getLearnerProfile().successfulRetries >= 1)
    }

    @Test
    fun testTutorLearnerStateReceivesLiveSpeechSignals() = runTest {
        val audioSamples = ShortArray(16000) { 800 }
        val segment = SpeechAudioSegment(samples = audioSamples, durationMs = 1000L)

        engine.processUserUtterance("I am preparing for this interview.", audioSegment = segment)

        val state = learningMemoryManager.buildLearnerState()
        assertNotNull("latestQuality should not be null when audio is analyzed", state.latestQuality)
        assertNotNull("latestFluency should not be null when audio is analyzed", state.latestFluency)
        assertTrue("latestQuality should confirm signal usability", state.latestQuality?.isSignalUsable == true)
    }
}
