package com.vaniflow.app.engine.learning.tutor

import com.vaniflow.app.engine.learning.tutor.model.CorrectionPolicyState
import com.vaniflow.app.engine.learning.tutor.model.MasteryState
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class CorrectionPolicyEngineTest {

    private lateinit var policyEngine: CorrectionPolicyEngine

    @Before
    fun setup() {
        policyEngine = CorrectionPolicyEngine()
    }

    @Test
    fun `important error triggers spoken correction normally`() {
        val decision = TutorCorrectionDecision(
            hasError = true,
            primarySeverity = CorrectionSeverity.IMPORTANT,
            timing = CorrectionTiming.AFTER_UTTERANCE,
            shouldRequestRetry = true,
            detectedErrors = listOf(
                EnglishError(
                    originalText = "buyed",
                    suggestedText = "bought",
                    category = EnglishErrorCategory.TENSE,
                    severity = CorrectionSeverity.IMPORTANT,
                    explanation = "Use 'bought' instead of 'buyed'.",
                    ruleIdentifier = "past_buyed"
                )
            )
        )

        val policyState = CorrectionPolicyState()
        val shouldSpoken = policyEngine.shouldDeliverSpokenCorrection(decision, null, policyState, 75f)
        assertTrue("Important error must trigger spoken correction", shouldSpoken)
    }

    @Test
    fun `concept in active cooldown suppresses spoken correction`() {
        val decision = TutorCorrectionDecision(
            hasError = true,
            primarySeverity = CorrectionSeverity.IMPORTANT,
            timing = CorrectionTiming.AFTER_UTTERANCE,
            shouldRequestRetry = true,
            detectedErrors = listOf(
                EnglishError(
                    originalText = "buyed",
                    suggestedText = "bought",
                    category = EnglishErrorCategory.TENSE,
                    severity = CorrectionSeverity.IMPORTANT,
                    explanation = "Use 'bought' instead of 'buyed'.",
                    ruleIdentifier = "past_buyed"
                )
            )
        )

        val policyState = CorrectionPolicyState()
        policyState.setConceptCooldown("past_buyed", 60_000L) // 1 min cooldown

        val shouldSpoken = policyEngine.shouldDeliverSpokenCorrection(decision, null, policyState, 75f)
        assertFalse("Concept in cooldown must NOT interrupt learner with spoken correction", shouldSpoken)
    }

    @Test
    fun `struggle backoff suppresses non-critical corrections to protect conversation flow`() {
        val decision = TutorCorrectionDecision(
            hasError = true,
            primarySeverity = CorrectionSeverity.IMPORTANT,
            timing = CorrectionTiming.AFTER_UTTERANCE,
            shouldRequestRetry = true,
            detectedErrors = listOf(
                EnglishError(
                    originalText = "buyed",
                    suggestedText = "bought",
                    category = EnglishErrorCategory.TENSE,
                    severity = CorrectionSeverity.IMPORTANT,
                    explanation = "Use 'bought'.",
                    ruleIdentifier = "past_buyed"
                )
            )
        )

        val policyState = CorrectionPolicyState(isStruggleBackoffActive = true)
        val shouldSpoken = policyEngine.shouldDeliverSpokenCorrection(decision, null, policyState, 50f)
        assertFalse("Struggle backoff must protect flow and suppress non-critical spoken interruptions", shouldSpoken)
    }

    @Test
    fun `mastered concept suppresses isolated minor slip`() {
        val decision = TutorCorrectionDecision(
            hasError = true,
            primarySeverity = CorrectionSeverity.IMPORTANT,
            timing = CorrectionTiming.AFTER_UTTERANCE,
            shouldRequestRetry = true,
            detectedErrors = listOf(
                EnglishError(
                    originalText = "buyed",
                    suggestedText = "bought",
                    category = EnglishErrorCategory.TENSE,
                    severity = CorrectionSeverity.IMPORTANT,
                    explanation = "Use 'bought'.",
                    ruleIdentifier = "past_buyed"
                )
            )
        )

        val mastery = MasteryState(
            conceptId = "past_buyed",
            category = EnglishErrorCategory.TENSE,
            masteryScore = 90
        )
        val policyState = CorrectionPolicyState()
        val shouldSpoken = policyEngine.shouldDeliverSpokenCorrection(decision, mastery, policyState, 80f)
        assertFalse("Mastered concept should suppress non-critical spoken interruptions", shouldSpoken)
    }
}
