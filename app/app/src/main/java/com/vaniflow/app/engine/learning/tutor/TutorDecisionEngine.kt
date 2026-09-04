package com.vaniflow.app.engine.learning.tutor

import com.vaniflow.app.engine.learning.tutor.model.CorrectionPolicyState
import com.vaniflow.app.engine.learning.tutor.model.DifficultyLevel
import com.vaniflow.app.engine.learning.tutor.model.MasteryState
import com.vaniflow.app.engine.learning.tutor.model.TutorAction
import com.vaniflow.app.engine.learning.tutor.model.TutorDecision
import com.vaniflow.app.engine.learning.tutor.model.TutorLearnerState
import com.vaniflow.app.engine.learning.tutor.model.TutorResponsePlan
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Central Adaptive AI Tutor Brain.
 *
 * Deterministically evaluates the unified learner state to decide the single best
 * next teaching action (correct, retry, praise, struggle backoff, weak concept practice,
 * vocabulary recycling, difficulty adaptation, or natural conversational flow).
 */
@Singleton
class TutorDecisionEngine @Inject constructor(
    val correctionPolicyEngine: CorrectionPolicyEngine,
    val difficultyEngine: DifficultyEngine
) {
    // Secondary constructor for testing convenience
    constructor() : this(CorrectionPolicyEngine(), DifficultyEngine())


    fun evaluateDecision(
        state: TutorLearnerState,
        rawCorrectionDecision: TutorCorrectionDecision? = null,
        retryEvaluation: RetryEvaluation? = null
    ): TutorDecision {
        // -------------------------------------------------------------
        // Priority 1: System / Session Limit Constraints
        // -------------------------------------------------------------
        if (state.sessionDurationMs >= 45 * 60 * 1000L && state.sessionTurnCount >= 30) {
            return TutorDecision(
                action = TutorAction.END_SESSION,
                reason = "Target session duration reached; wrap up smoothly",
                shouldInterruptTurn = false,
                adaptiveDifficulty = state.currentDifficulty,
                coachingDirective = "Gently conclude the conversation with a positive summary."
            )
        }

        // -------------------------------------------------------------
        // Priority 2: Active Retry State
        // -------------------------------------------------------------
        if (state.isRetryActive) {
            if (retryEvaluation != null && retryEvaluation.isFixed) {
                return TutorDecision(
                    action = TutorAction.PRAISE_SUCCESS,
                    reason = "Learner successfully corrected the targeted error",
                    shouldInterruptTurn = true,
                    spokenInterventionText = retryEvaluation.praiseFeedback,
                    adaptiveDifficulty = state.currentDifficulty,
                    coachingDirective = "Praise success and continue conversation."
                )
            } else if (state.retryAttemptsCount < 2) {
                val orig = state.activeRetryError
                val hint = if (orig != null) {
                    "Almost! Remember: say '${orig.suggestedText}' instead of '${orig.originalText}'. Try it once more."
                } else {
                    "Almost! Give it one more try."
                }
                return TutorDecision(
                    action = TutorAction.GIVE_SECOND_HINT,
                    reason = "Second attempt needed on targeted retry",
                    shouldInterruptTurn = true,
                    spokenInterventionText = hint,
                    adaptiveDifficulty = state.currentDifficulty,
                    coachingDirective = "Provide gentle second hint."
                )
            } else {
                val orig = state.activeRetryError
                val exitMsg = if (orig != null) {
                    "Good try! The natural way is: \"${orig.suggestedText}\". Let's keep going!"
                } else {
                    "Good try! Let's keep going!"
                }
                return TutorDecision(
                    action = TutorAction.NORMAL_CONVERSATION,
                    reason = "Max retry attempts reached; release retry lock without frustrating user",
                    shouldInterruptTurn = true,
                    spokenInterventionText = exitMsg,
                    adaptiveDifficulty = state.currentDifficulty,
                    coachingDirective = "Acknowledge effort and return to conversation."
                )
            }
        }

        // -------------------------------------------------------------
        // Priority 3: Critical Error (Safety / Serious Communication Break)
        // -------------------------------------------------------------
        if (rawCorrectionDecision != null && rawCorrectionDecision.hasError && rawCorrectionDecision.detectedErrors.isNotEmpty()) {
            val criticalError = rawCorrectionDecision.detectedErrors.firstOrNull { it.severity == CorrectionSeverity.CRITICAL }
            if (criticalError != null) {
                val correctedText = rawCorrectionDecision.correctedSentence ?: criticalError.suggestedText
                val spoken = "${criticalError.explanation} Try saying: \"$correctedText\""
                return TutorDecision(
                    action = TutorAction.CRITICAL_CORRECTION,
                    reason = "Critical communication or offensive misunderstanding detected",
                    shouldInterruptTurn = true,
                    spokenInterventionText = spoken,
                    suggestedTargetConcept = criticalError.ruleIdentifier,
                    adaptiveDifficulty = state.currentDifficulty,
                    coachingDirective = "Address critical error immediately."
                )
            }
        }

        // -------------------------------------------------------------
        // Priority 4: Important Correction & Spoken Retry Request
        // -------------------------------------------------------------
        if (rawCorrectionDecision != null && rawCorrectionDecision.hasError && rawCorrectionDecision.detectedErrors.isNotEmpty()) {
            val primary = rawCorrectionDecision.detectedErrors.first()
            val score = state.conceptMasteryScores[primary.ruleIdentifier] ?: 50
            val masteryObj = MasteryState(
                conceptId = primary.ruleIdentifier,
                category = primary.category,
                masteryScore = score
            )
            val policyState = CorrectionPolicyState(
                isStruggleBackoffActive = state.isStruggleBackoffActive,
                consecutiveErrorsCount = state.consecutiveFailures
            )

            val shouldSpoken = correctionPolicyEngine.shouldDeliverSpokenCorrection(
                rawDecision = rawCorrectionDecision,
                mastery = masteryObj,
                policyState = policyState,
                speakingConfidence = state.speakingConfidence
            )

            if (shouldSpoken) {
                val correctedText = rawCorrectionDecision.correctedSentence ?: primary.suggestedText
                val spoken = "${primary.explanation} Try saying: \"$correctedText\""
                return TutorDecision(
                    action = TutorAction.ASK_RETRY,
                    reason = "Important learning mistake identified with active retry requested",
                    shouldInterruptTurn = true,
                    spokenInterventionText = spoken,
                    suggestedTargetConcept = primary.ruleIdentifier,
                    adaptiveDifficulty = state.currentDifficulty,
                    coachingDirective = "Request spoken retry for ${primary.ruleIdentifier}."
                )
            }
        }

        // -------------------------------------------------------------
        // Priority 5: Struggle Backoff & Confidence Protection
        // -------------------------------------------------------------
        if (state.isStruggleBackoffActive || state.speakingConfidence < 40f || state.consecutiveFailures >= 3) {
            val lowerDiff = difficultyEngine.determineDifficulty(
                estimatedLevel = state.cefrLevel,
                speakingConfidence = state.speakingConfidence,
                successfulRetriesCount = state.successfulRetriesCount,
                totalUtterances = state.sessionTurnCount
            )
            return TutorDecision(
                action = TutorAction.ENCOURAGE_LEARNER,
                reason = "Learner under cognitive load; suppressing non-critical interruptions to rebuild confidence",
                shouldInterruptTurn = false,
                adaptiveDifficulty = lowerDiff,
                coachingDirective = "Learner is struggling or low on confidence. Respond warmly and supportively, keep sentence structures clear and simple, and maintain comfortable speaking flow."
            )
        }

        // -------------------------------------------------------------
        // Priority 6: Passive / Contextual Correction (Non-Interruptive)
        // -------------------------------------------------------------
        if (rawCorrectionDecision != null && rawCorrectionDecision.hasError && rawCorrectionDecision.detectedErrors.isNotEmpty()) {
            val primary = rawCorrectionDecision.detectedErrors.first()
            return TutorDecision(
                action = TutorAction.PASSIVE_CORRECTION,
                reason = "Minor or stylistic slip; recorded for review, modeled subtly in dialogue",
                shouldInterruptTurn = false,
                suggestedTargetConcept = primary.ruleIdentifier,
                adaptiveDifficulty = state.currentDifficulty,
                coachingDirective = "Subtly and naturally model the correct phrase '${primary.suggestedText}' in your response without lecturing or stopping the conversation."
            )
        }

        // -------------------------------------------------------------
        // Priority 7: Weak Concept Practice Steering
        // -------------------------------------------------------------
        if (state.weakestConcepts.isNotEmpty()) {
            val weak = state.weakestConcepts.first()
            val score = state.conceptMasteryScores[weak] ?: 40
            if (score < 65) {
                val directive = when {
                    weak.contains("tense") -> "Learner is practicing past tense verbs. Naturally ask an engaging question about what they did yesterday, over the weekend, or in the past."
                    weak.contains("agreement") -> "Learner is practicing subject-verb agreement. Keep your examples clear and natural."
                    weak.contains("article") -> "Learner is refining article usage (a/an/the). Model clear noun phrases."
                    weak.contains("preposition") -> "Learner is practicing prepositions ('good at', 'interested in')."
                    else -> "Naturally weave practice opportunities for $weak into the dialogue."
                }
                return TutorDecision(
                    action = TutorAction.PRACTICE_WEAK_CONCEPT,
                    reason = "Proactively steering dialogue to practice weak concept: $weak",
                    shouldInterruptTurn = false,
                    suggestedTargetConcept = weak,
                    adaptiveDifficulty = state.currentDifficulty,
                    coachingDirective = directive
                )
            }
        }

        // -------------------------------------------------------------
        // Priority 8: Vocabulary Recycling
        // -------------------------------------------------------------
        if (state.vocabularyNeedingPractice.isNotEmpty()) {
            val vocab = state.vocabularyNeedingPractice.first()
            return TutorDecision(
                action = TutorAction.REUSE_VOCABULARY,
                reason = "Recycling previously learned expression: ${vocab.wordOrPhrase}",
                shouldInterruptTurn = false,
                suggestedVocabularyToReuse = vocab.wordOrPhrase,
                adaptiveDifficulty = state.currentDifficulty,
                coachingDirective = "Naturally elicit or model the expression \"${vocab.wordOrPhrase}\" in your response."
            )
        }

        // -------------------------------------------------------------
        // Priority 9: Adaptive Difficulty Escalation
        // -------------------------------------------------------------
        if (state.speakingConfidence >= 85f && state.sessionTurnCount >= 15 && state.consecutiveFailures == 0) {
            val higherDiff = difficultyEngine.determineDifficulty(
                estimatedLevel = state.cefrLevel,
                speakingConfidence = state.speakingConfidence,
                successfulRetriesCount = state.successfulRetriesCount,
                totalUtterances = state.sessionTurnCount
            )
            if (higherDiff > state.currentDifficulty) {
                return TutorDecision(
                    action = TutorAction.INCREASE_DIFFICULTY,
                    reason = "High confidence and consistent accuracy observed; escalating challenge level",
                    shouldInterruptTurn = false,
                    adaptiveDifficulty = higherDiff,
                    coachingDirective = "Learner is speaking fluently and accurately. Challenge them with richer vocabulary and more nuanced questions."
                )
            }
        }

        // -------------------------------------------------------------
        // Priority 10: Active Session Learning Goal Alignment
        // -------------------------------------------------------------
        if (state.activeGoals.isNotEmpty()) {
            val goal = state.activeGoals.firstOrNull { !it.isAchieved }
            if (goal != null) {
                return TutorDecision(
                    action = TutorAction.CONTINUE_SCENARIO,
                    reason = "Advancing active session learning goal: ${goal.title}",
                    shouldInterruptTurn = false,
                    adaptiveDifficulty = state.currentDifficulty,
                    coachingDirective = "Focus on session objective: ${goal.title} — ${goal.description}"
                )
            }
        }

        // -------------------------------------------------------------
        // Priority 11: Normal Conversational Flow
        // -------------------------------------------------------------
        val hesitationDirective = if (state.latestFluency?.hesitationType == com.vaniflow.app.engine.speech.model.HesitationType.LONG_HESITATION ||
            state.latestFluency?.hesitationType == com.vaniflow.app.engine.speech.model.HesitationType.REPEATED_HESITATION) {
            "Learner is taking time to construct sentences; maintain patient, supportive conversational pacing."
        } else null

        val speechDirective = when {
            state.latestPronunciation?.practiceSoundSuggestion != null ->
                "Speech Guidance: ${state.latestPronunciation.practiceSoundSuggestion}"
            hesitationDirective != null ->
                "Speech Guidance: $hesitationDirective"
            else ->
                "Speech Guidance: Maintain natural conversational rhythm and comfortable pacing."
        }

        return TutorDecision(
            action = TutorAction.NORMAL_CONVERSATION,
            reason = "Conversational flow is healthy and clean",
            shouldInterruptTurn = false,
            adaptiveDifficulty = state.currentDifficulty,
            coachingDirective = "Respond naturally and authentically. $speechDirective"
        )
    }

    /**
     * Constructs a deterministic, typed [TutorResponsePlan] contract for the AI generation layer.
     * Enforces that the LLM verbalizes the exact pedagogical intention without inventing corrections.
     */
    fun createResponsePlan(
        decision: TutorDecision,
        correctionDecision: TutorCorrectionDecision? = null,
        characterId: String = "raya",
        contextSummary: String? = null
    ): TutorResponsePlan {
        return when (decision.action) {
            TutorAction.IMPORTANT_CORRECTION, TutorAction.CRITICAL_CORRECTION, TutorAction.ASK_RETRY -> {
                val corrected = decision.spokenInterventionText ?: correctionDecision?.correctedSentence
                TutorResponsePlan(
                    tutorAction = decision.action,
                    correctionRequired = true,
                    correctionText = corrected,
                    learnerRetryRequired = true,
                    conversationIntent = "CORRECTION_AND_PRACTICE",
                    contextSummary = contextSummary,
                    characterStyle = characterId,
                    generationInstruction = "Correct the learner's error. Use one concise explanation. Give the corrected sentence: '${corrected ?: "the natural phrasing"}'. Ask the learner to retry. Do not praise excessively. After retry, resume natural conversation."
                )
            }
            TutorAction.PRAISE_SUCCESS -> {
                TutorResponsePlan(
                    tutorAction = decision.action,
                    correctionRequired = false,
                    learnerRetryRequired = false,
                    conversationIntent = "PRAISE_AND_CONTINUE",
                    contextSummary = contextSummary,
                    characterStyle = characterId,
                    generationInstruction = "Acknowledge the learner's correct retry with warm confirmation ('Yes, exactly right!'), then naturally continue the conversation."
                )
            }
            TutorAction.GIVE_SECOND_HINT -> {
                TutorResponsePlan(
                    tutorAction = decision.action,
                    correctionRequired = true,
                    learnerRetryRequired = true,
                    conversationIntent = "SECOND_HINT",
                    contextSummary = contextSummary,
                    characterStyle = characterId,
                    generationInstruction = "Provide a gentle second hint for the target phrasing and invite them to try one more time."
                )
            }
            TutorAction.PRACTICE_WEAK_CONCEPT -> {
                TutorResponsePlan(
                    tutorAction = decision.action,
                    targetConcept = decision.suggestedTargetConcept,
                    conversationIntent = "CONCEPT_PRACTICE",
                    contextSummary = contextSummary,
                    characterStyle = characterId,
                    generationInstruction = "Guide the conversation to naturally elicit or practice: ${decision.suggestedTargetConcept}."
                )
            }
            TutorAction.REUSE_VOCABULARY -> {
                TutorResponsePlan(
                    tutorAction = decision.action,
                    targetConcept = decision.suggestedVocabularyToReuse,
                    conversationIntent = "VOCABULARY_RECYCLING",
                    contextSummary = contextSummary,
                    characterStyle = characterId,
                    generationInstruction = "Naturally include or elicit the vocabulary word '${decision.suggestedVocabularyToReuse}' in your response."
                )
            }
            else -> {
                TutorResponsePlan(
                    tutorAction = decision.action,
                    correctionRequired = false,
                    learnerRetryRequired = false,
                    conversationIntent = "NORMAL_CONVERSATION",
                    contextSummary = contextSummary,
                    characterStyle = characterId,
                    generationInstruction = "Respond conversationally, warmly, and relevantly to what the user said. Directly address any question they asked before asking a follow-up. Do not invent grammar errors."
                )
            }
        }
    }
}
