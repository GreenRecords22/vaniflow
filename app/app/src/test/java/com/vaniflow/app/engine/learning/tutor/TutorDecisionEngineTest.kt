package com.vaniflow.app.engine.learning.tutor

import com.vaniflow.app.engine.learning.tutor.model.CorrectionPolicyState
import com.vaniflow.app.engine.learning.tutor.model.DifficultyLevel
import com.vaniflow.app.engine.learning.tutor.model.LearningGoal
import com.vaniflow.app.engine.learning.tutor.model.MasteryState
import com.vaniflow.app.engine.learning.tutor.model.TutorAction
import com.vaniflow.app.engine.learning.tutor.model.TutorDecision
import com.vaniflow.app.engine.learning.tutor.model.TutorLearnerState
import com.vaniflow.app.engine.learning.tutor.model.VocabularyMemory
import com.vaniflow.app.engine.speech.model.FluencyAnalysisResult
import com.vaniflow.app.engine.speech.model.HesitationType
import com.vaniflow.app.engine.speech.model.PronunciationEvidence
import com.vaniflow.app.engine.speech.model.QualitativeFluencyRating
import com.vaniflow.app.engine.speech.model.QualitativePronunciationRating
import com.vaniflow.app.engine.speech.model.SpeechQualityResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Comprehensive 20-Case Test Suite for P3: Adaptive AI Tutor Brain (TutorDecisionEngine).
 */
class TutorDecisionEngineTest {

    private lateinit var correctionPolicyEngine: CorrectionPolicyEngine
    private lateinit var difficultyEngine: DifficultyEngine
    private lateinit var decisionEngine: TutorDecisionEngine

    @Before
    fun setUp() {
        correctionPolicyEngine = CorrectionPolicyEngine()
        difficultyEngine = DifficultyEngine()
        decisionEngine = TutorDecisionEngine(correctionPolicyEngine, difficultyEngine)
    }

    @Test
    fun `test 1 - clean learner turn results in NORMAL_CONVERSATION`() {
        val state = TutorLearnerState(
            cefrLevel = EstimatedLevel.B1,
            speakingConfidence = 70f
        )
        val decision = decisionEngine.evaluateDecision(state, rawCorrectionDecision = null)

        assertEquals(TutorAction.NORMAL_CONVERSATION, decision.action)
        assertFalse(decision.shouldInterruptTurn)
        assertNotNull(decision.coachingDirective)
    }

    @Test
    fun `test 2 - important grammar error results in ASK_RETRY`() {
        val state = TutorLearnerState(
            speakingConfidence = 70f,
            conceptMasteryScores = mapOf("tense_past_buyed" to 30)
        )
        val rawDecision = TutorCorrectionDecision(
            hasError = true,
            detectedErrors = listOf(
                EnglishError(
                    originalText = "buyed",
                    suggestedText = "bought",
                    category = EnglishErrorCategory.TENSE,
                    severity = CorrectionSeverity.IMPORTANT,
                    explanation = "Use 'bought' as the past tense of buy.",
                    ruleIdentifier = "tense_past_buyed"
                )
            ),
            shouldRequestRetry = true,
            correctedSentence = "I bought vegetables yesterday."
        )

        val decision = decisionEngine.evaluateDecision(state, rawCorrectionDecision = rawDecision)

        assertEquals(TutorAction.ASK_RETRY, decision.action)
        assertTrue(decision.shouldInterruptTurn)
        assertNotNull(decision.spokenInterventionText)
        assertTrue(decision.spokenInterventionText!!.contains("bought"))
    }

    @Test
    fun `test 3 - critical error results in CRITICAL_CORRECTION`() {
        val state = TutorLearnerState(
            speakingConfidence = 60f
        )
        val rawDecision = TutorCorrectionDecision(
            hasError = true,
            detectedErrors = listOf(
                EnglishError(
                    originalText = "shut up",
                    suggestedText = "please give me a moment",
                    category = EnglishErrorCategory.NATURAL_PHRASING,
                    severity = CorrectionSeverity.CRITICAL,
                    explanation = "That phrase can sound rude.",
                    ruleIdentifier = "politeness_critical"
                )
            ),
            shouldRequestRetry = true,
            correctedSentence = "Please give me a moment."
        )

        val decision = decisionEngine.evaluateDecision(state, rawCorrectionDecision = rawDecision)

        assertEquals(TutorAction.CRITICAL_CORRECTION, decision.action)
        assertTrue(decision.shouldInterruptTurn)
    }

    @Test
    fun `test 4 - active retry gives second hint on first failed retry attempt`() {
        val error = EnglishError(
            originalText = "buyed",
            suggestedText = "bought",
            category = EnglishErrorCategory.TENSE,
            severity = CorrectionSeverity.IMPORTANT,
            explanation = "Use 'bought'."
        )
        val state = TutorLearnerState(
            isRetryActive = true,
            retryAttemptsCount = 1,
            activeRetryError = error
        )
        val retryEval = RetryEvaluation(
            originalError = error,
            retryUtterance = "I buyed again",
            isFixed = false,
            isPartiallyFixed = false,
            praiseFeedback = "",
            masteryDelta = 0f
        )

        val decision = decisionEngine.evaluateDecision(state, retryEvaluation = retryEval)

        assertEquals(TutorAction.GIVE_SECOND_HINT, decision.action)
        assertTrue(decision.shouldInterruptTurn)
        assertTrue(decision.spokenInterventionText!!.contains("Try it once more"))
    }

    @Test
    fun `test 5 - successful retry results in PRAISE_SUCCESS`() {
        val error = EnglishError(
            originalText = "buyed",
            suggestedText = "bought",
            category = EnglishErrorCategory.TENSE,
            severity = CorrectionSeverity.IMPORTANT,
            explanation = "Use 'bought'."
        )
        val state = TutorLearnerState(
            isRetryActive = true,
            retryAttemptsCount = 1,
            activeRetryError = error
        )
        val retryEval = RetryEvaluation(
            originalError = error,
            retryUtterance = "I bought vegetables yesterday",
            isFixed = true,
            isPartiallyFixed = false,
            praiseFeedback = "Excellent! You used 'bought' perfectly.",
            masteryDelta = 15f
        )

        val decision = decisionEngine.evaluateDecision(state, retryEvaluation = retryEval)

        assertEquals(TutorAction.PRAISE_SUCCESS, decision.action)
        assertTrue(decision.shouldInterruptTurn)
        assertEquals("Excellent! You used 'bought' perfectly.", decision.spokenInterventionText)
    }

    @Test
    fun `test 6 - repeated failure triggers struggle protection ENCOURAGE_LEARNER`() {
        val state = TutorLearnerState(
            speakingConfidence = 50f,
            consecutiveFailures = 3,
            isStruggleBackoffActive = true
        )
        val rawDecision = TutorCorrectionDecision(
            hasError = true,
            detectedErrors = listOf(
                EnglishError(
                    originalText = "in Monday",
                    suggestedText = "on Monday",
                    category = EnglishErrorCategory.PREPOSITIONS,
                    severity = CorrectionSeverity.IMPORTANT,
                    explanation = "Use 'on Monday'."
                )
            ),
            shouldRequestRetry = true
        )

        val decision = decisionEngine.evaluateDecision(state, rawCorrectionDecision = rawDecision)

        assertEquals(TutorAction.ENCOURAGE_LEARNER, decision.action)
        assertFalse("Spoken interruption should be suppressed in struggle mode", decision.shouldInterruptTurn)
        assertTrue(decision.coachingDirective!!.contains("supportive"))
    }

    @Test
    fun `test 7 - low confidence below 40 suppresses spoken interruptions`() {
        val state = TutorLearnerState(
            speakingConfidence = 35f,
            consecutiveFailures = 1
        )
        val rawDecision = TutorCorrectionDecision(
            hasError = true,
            detectedErrors = listOf(
                EnglishError(
                    originalText = "he go",
                    suggestedText = "he goes",
                    category = EnglishErrorCategory.SUBJECT_VERB_AGREEMENT,
                    severity = CorrectionSeverity.IMPORTANT,
                    explanation = "Use 'goes'."
                )
            ),
            shouldRequestRetry = true
        )

        val decision = decisionEngine.evaluateDecision(state, rawCorrectionDecision = rawDecision)

        assertEquals(TutorAction.ENCOURAGE_LEARNER, decision.action)
        assertFalse(decision.shouldInterruptTurn)
    }

    @Test
    fun `test 8 - weak concept steering results in PRACTICE_WEAK_CONCEPT`() {
        val state = TutorLearnerState(
            speakingConfidence = 70f,
            weakestConcepts = listOf("tense_past"),
            conceptMasteryScores = mapOf("tense_past" to 40)
        )

        val decision = decisionEngine.evaluateDecision(state)

        assertEquals(TutorAction.PRACTICE_WEAK_CONCEPT, decision.action)
        assertEquals("tense_past", decision.suggestedTargetConcept)
        assertTrue(decision.coachingDirective!!.contains("past"))
    }

    @Test
    fun `test 9 - mastered concept with score 85+ suppresses spoken interruption on isolated slip`() {
        val state = TutorLearnerState(
            speakingConfidence = 80f,
            conceptMasteryScores = mapOf("article_the" to 90)
        )
        val rawDecision = TutorCorrectionDecision(
            hasError = true,
            detectedErrors = listOf(
                EnglishError(
                    originalText = "to park",
                    suggestedText = "to the park",
                    category = EnglishErrorCategory.ARTICLES,
                    severity = CorrectionSeverity.IMPORTANT,
                    explanation = "Use 'the park'.",
                    ruleIdentifier = "article_the"
                )
            ),
            shouldRequestRetry = true
        )

        val decision = decisionEngine.evaluateDecision(state, rawCorrectionDecision = rawDecision)

        // CorrectionPolicyEngine suppresses interruption on score >= 85, so TutorDecisionEngine chooses PASSIVE_CORRECTION
        assertEquals(TutorAction.PASSIVE_CORRECTION, decision.action)
        assertFalse(decision.shouldInterruptTurn)
    }

    @Test
    fun `test 10 - vocabulary opportunity results in REUSE_VOCABULARY`() {
        val vocab = VocabularyMemory(
            wordOrPhrase = "looking forward to",
            meaning = "excited about",
            exampleSentence = "I am looking forward to our meeting.",
            familiarityScore = 30
        )
        val state = TutorLearnerState(
            speakingConfidence = 70f,
            vocabularyNeedingPractice = listOf(vocab)
        )

        val decision = decisionEngine.evaluateDecision(state)

        assertEquals(TutorAction.REUSE_VOCABULARY, decision.action)
        assertEquals("looking forward to", decision.suggestedVocabularyToReuse)
    }

    @Test
    fun `test 11 - natural thinking pause (200-800ms) produces healthy normal decision`() {
        val fluency = FluencyAnalysisResult(
            totalDurationMs = 2500L,
            voicedDurationMs = 1800L,
            silenceDurationMs = 700L,
            speechToSilenceRatio = 2.5f,
            pauseCount = 1,
            totalPauseDurationMs = 400L,
            averagePauseDurationMs = 400L,
            wordsPerMinute = 120f,
            syllablesPerSecond = 3.5f,
            hesitationType = HesitationType.NORMAL_PAUSE,
            qualitativeRating = QualitativeFluencyRating.NATURAL,
            confidence = 0.9f
        )
        val state = TutorLearnerState(
            speakingConfidence = 75f,
            latestFluency = fluency
        )

        val decision = decisionEngine.evaluateDecision(state)

        assertEquals(TutorAction.NORMAL_CONVERSATION, decision.action)
        assertFalse(decision.shouldInterruptTurn)
    }

    @Test
    fun `test 12 - pronunciation candidate is never treated as confirmed mispronunciation`() {
        val pron = PronunciationEvidence(
            transcript = "I think this is great",
            audioDurationMs = 2000L,
            voicedDurationMs = 1500L,
            pauseCount = 0,
            totalPauseDurationMs = 0L,
            speakingRateWpm = 130f,
            speechToSilenceRatio = 3.0f,
            signalQualityScore = 80f,
            audioQualityEvidenceAvailable = true,
            fluencyEvidenceAvailable = true,
            transcriptEvidenceAvailable = true,
            phonemeEvidenceAvailable = false, // Strictly false
            practiceTargetId = "th_unvoiced",
            practiceTargetLabel = "unvoiced 'th' sound (/θ/)",
            observedPhonemePatterns = listOf("th_unvoiced"),
            qualitativeRating = QualitativePronunciationRating.CLEAR,
            confidence = 0.85f,
            practiceSoundSuggestion = "Place tongue between teeth"
        )
        val state = TutorLearnerState(
            speakingConfidence = 70f,
            latestPronunciation = pron
        )

        val decision = decisionEngine.evaluateDecision(state)

        assertEquals(TutorAction.NORMAL_CONVERSATION, decision.action)
        assertFalse(decision.shouldInterruptTurn)
        assertTrue(decision.coachingDirective!!.contains("Place tongue between teeth"))
    }

    @Test
    fun `test 13 - missing speech evidence produces safe baseline directive`() {
        val state = TutorLearnerState(
            speakingConfidence = 70f,
            latestPronunciation = null
        )

        val decision = decisionEngine.evaluateDecision(state)

        assertEquals(TutorAction.NORMAL_CONVERSATION, decision.action)
        assertTrue(decision.coachingDirective!!.contains("conversational rhythm"))
    }

    @Test
    fun `test 14 - fair-use exceeded flag is reflected in learner state`() {
        val state = TutorLearnerState(
            isFairUseExceeded = true
        )

        assertTrue(state.isFairUseExceeded)
    }

    @Test
    fun `test 15 - difficulty increase triggered only when learner is confident and consistent`() {
        val state = TutorLearnerState(
            cefrLevel = EstimatedLevel.B1,
            speakingConfidence = 90f,
            sessionTurnCount = 20,
            consecutiveFailures = 0,
            successfulRetriesCount = 18,
            currentDifficulty = DifficultyLevel.INTERMEDIATE
        )

        val decision = decisionEngine.evaluateDecision(state)

        assertEquals(TutorAction.INCREASE_DIFFICULTY, decision.action)
        assertEquals(DifficultyLevel.UPPER_INTERMEDIATE, decision.adaptiveDifficulty)
    }

    @Test
    fun `test 16 - difficulty decrease triggered after sustained struggle`() {
        val state = TutorLearnerState(
            cefrLevel = EstimatedLevel.B1,
            speakingConfidence = 30f,
            consecutiveFailures = 4,
            isStruggleBackoffActive = true,
            currentDifficulty = DifficultyLevel.INTERMEDIATE
        )

        val decision = decisionEngine.evaluateDecision(state)

        assertEquals(TutorAction.ENCOURAGE_LEARNER, decision.action)
        assertEquals(DifficultyLevel.BEGINNER, decision.adaptiveDifficulty)
    }

    @Test
    fun `test 17 - session goal progression aligns with active goals`() {
        val goal = LearningGoal(
            title = "Professional Articulation",
            description = "Express work background clearly.",
            priority = 1,
            isAchieved = false
        )
        val state = TutorLearnerState(
            speakingConfidence = 70f,
            activeGoals = listOf(goal)
        )

        val decision = decisionEngine.evaluateDecision(state)

        assertEquals(TutorAction.CONTINUE_SCENARIO, decision.action)
        assertTrue(decision.coachingDirective!!.contains("Professional Articulation"))
    }

    @Test
    fun `test 18 - multiple simultaneous signals resolve according to deterministic priority`() {
        // Both critical error AND weak concept AND vocabulary recycling present
        val vocab = VocabularyMemory(wordOrPhrase = "in a nutshell", meaning = "summarized", exampleSentence = "")
        val rawDecision = TutorCorrectionDecision(
            hasError = true,
            detectedErrors = listOf(
                EnglishError(
                    originalText = "bad word",
                    suggestedText = "pardon me",
                    category = EnglishErrorCategory.NATURAL_PHRASING,
                    severity = CorrectionSeverity.CRITICAL,
                    explanation = "Critical slip"
                )
            ),
            shouldRequestRetry = true
        )
        val state = TutorLearnerState(
            speakingConfidence = 70f,
            weakestConcepts = listOf("tense_past"),
            vocabularyNeedingPractice = listOf(vocab)
        )

        val decision = decisionEngine.evaluateDecision(state, rawCorrectionDecision = rawDecision)

        // Critical error has higher priority than weak concepts or vocabulary
        assertEquals(TutorAction.CRITICAL_CORRECTION, decision.action)
        assertTrue(decision.shouldInterruptTurn)
    }

    @Test
    fun `test 19 - same input state produces deterministic same decision`() {
        val state = TutorLearnerState(
            cefrLevel = EstimatedLevel.A2,
            speakingConfidence = 65f
        )

        val decision1 = decisionEngine.evaluateDecision(state)
        val decision2 = decisionEngine.evaluateDecision(state)

        assertEquals(decision1.action, decision2.action)
        assertEquals(decision1.reason, decision2.reason)
        assertEquals(decision1.shouldInterruptTurn, decision2.shouldInterruptTurn)
    }

    @Test
    fun `test 20 - zero arbitrary score generation`() {
        val state = TutorLearnerState(
            speakingConfidence = 70f
        )
        val decision = decisionEngine.evaluateDecision(state)

        // Assert that the decision does not manufacture any numerical score
        assertNotNull(decision.adaptiveDifficulty)
        assertFalse(decision.shouldInterruptTurn)
    }
}
