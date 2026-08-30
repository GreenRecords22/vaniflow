package com.vaniflow.app.engine.learning.tutor

import com.vaniflow.app.engine.learning.tutor.model.CorrectionPolicyState
import com.vaniflow.app.engine.learning.tutor.model.MasteryState
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CorrectionPolicyEngine @Inject constructor() {

    fun shouldDeliverSpokenCorrection(
        rawDecision: TutorCorrectionDecision,
        mastery: MasteryState?,
        policyState: CorrectionPolicyState,
        speakingConfidence: Float
    ): Boolean {
        if (!rawDecision.hasError || rawDecision.detectedErrors.isEmpty()) return false

        val primaryError = rawDecision.detectedErrors.first()
        val conceptId = primaryError.ruleIdentifier

        // 1. Critical errors always get spoken correction (e.g. offensive/catastrophic misunderstandings)
        if (primaryError.severity == CorrectionSeverity.CRITICAL) {
            return true
        }

        // 2. Concept Cooldown Protection: If concept was corrected within cooldown window, suppress spoken interruption
        if (policyState.isConceptInCooldown(conceptId)) {
            return false
        }

        // 3. Struggle Backoff Mode: If learner is experiencing high cognitive load / multiple errors, back off spoken interruption unless error is CRITICAL
        if (policyState.isStruggleBackoffActive && primaryError.severity < CorrectionSeverity.CRITICAL) {
            return false
        }

        // 4. Low Confidence Protection: If confidence dropped below 40%, do not interrupt with spoken corrections for non-critical errors
        if (speakingConfidence < 40f && primaryError.severity < CorrectionSeverity.CRITICAL) {
            return false
        }

        // 5. High Mastery Suppression: If concept is already mastered (score >= 85), do not interrupt on an isolated slip unless CRITICAL
        if (mastery != null && mastery.masteryScore >= 85 && primaryError.severity < CorrectionSeverity.CRITICAL) {
            return false
        }

        // 6. Base Policy: IMPORTANT errors trigger spoken correction
        return primaryError.severity >= CorrectionSeverity.IMPORTANT && rawDecision.shouldRequestRetry
    }
}
