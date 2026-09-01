package com.vaniflow.app.engine.learning.tutor

import com.vaniflow.app.engine.learning.tutor.model.LearningEvent
import com.vaniflow.app.engine.learning.tutor.model.LearningEventType
import com.vaniflow.app.engine.learning.tutor.model.MasteryState
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ProgressCalculationEngineTest {

    private lateinit var engine: ProgressCalculationEngine

    @Before
    fun setup() {
        engine = ProgressCalculationEngine()
    }

    @Test
    fun `estimateCefrLevel upgrades with high accuracy and consistency`() {
        val profile = LearnerProfile(
            estimatedLevel = EstimatedLevel.A2,
            speakingConfidenceScore = 88f,
            totalUtterances = 40,
            correctionsDelivered = 2,
            successfulRetries = 2
        )

        val masteryList = listOf(
            MasteryState("past_buyed", EnglishErrorCategory.TENSE, masteryScore = 90, consecutiveSuccesses = 3),
            MasteryState("sva_he_dont", EnglishErrorCategory.SUBJECT_VERB_AGREEMENT, masteryScore = 85, consecutiveSuccesses = 3),
            MasteryState("article_a_an", EnglishErrorCategory.ARTICLES, masteryScore = 90, consecutiveSuccesses = 2)
        )

        val estimated = engine.estimateCefrLevel(profile, masteryList)
        assertTrue("Estimated level should be at least B2 with 95% accuracy and high mastery",
            estimated == EstimatedLevel.B2 || estimated == EstimatedLevel.C1)
    }

    @Test
    fun `estimateCefrLevel preserves baseline level when utterances are below 15`() {
        val profile = LearnerProfile(
            estimatedLevel = EstimatedLevel.A1,
            speakingConfidenceScore = 95f,
            totalUtterances = 8,
            correctionsDelivered = 0,
            successfulRetries = 0
        )

        val estimated = engine.estimateCefrLevel(profile, emptyList())
        assertEquals("Should preserve baseline A1 until at least 15 utterances provide sufficient evidence",
            EstimatedLevel.A1, estimated)
    }

    @Test
    fun `generateSessionSummary reflects actual session learning events`() {
        val events = listOf(
            LearningEvent(
                type = LearningEventType.CORRECTION,
                conceptId = "past_buyed",
                category = EnglishErrorCategory.TENSE,
                severity = CorrectionSeverity.IMPORTANT,
                originalUtterance = "Yesterday I buyed apples",
                correctedForm = "bought",
                isSuccess = false,
                sessionId = "test_session_1"
            ),
            LearningEvent(
                type = LearningEventType.SUCCESSFUL_RETRY,
                conceptId = "past_buyed",
                category = EnglishErrorCategory.TENSE,
                severity = CorrectionSeverity.IMPORTANT,
                originalUtterance = "Yesterday I bought apples",
                correctedForm = "bought",
                isSuccess = true,
                sessionId = "test_session_1"
            ),
            LearningEvent(
                type = LearningEventType.VOCABULARY_LEARNED,
                conceptId = "looking forward to",
                category = EnglishErrorCategory.VOCABULARY,
                severity = CorrectionSeverity.STYLE,
                originalUtterance = "I am looking forward to it",
                correctedForm = "looking forward to",
                isSuccess = true,
                sessionId = "test_session_1"
            )
        )

        val summary = engine.generateSessionSummary(
            sessionId = "test_session_1",
            sessionDurationMs = 300_000L, // 5 minutes
            userTurnsCount = 8,
            events = events,
            speakingConfidence = 80f
        )

        assertEquals("test_session_1", summary.sessionId)
        assertEquals(5, summary.speakingMinutes)
        assertEquals(8, summary.userTurnsCount)
        assertEquals(1, summary.correctionsCount)
        assertEquals(1, summary.successfulRetriesCount)
        assertTrue(summary.conceptsMasteredOrImproved.contains("past_buyed"))
        assertTrue(summary.newExpressionsLearned.contains("looking forward to"))
        assertEquals("Improving", summary.confidenceTrend)
        assertEquals("Natural", summary.clarityRating)
    }
}
