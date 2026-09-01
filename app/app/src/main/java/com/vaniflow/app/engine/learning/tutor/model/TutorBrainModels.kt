package com.vaniflow.app.engine.learning.tutor.model

import com.vaniflow.app.engine.learning.tutor.EstimatedLevel
import com.vaniflow.app.engine.learning.tutor.EnglishError
import com.vaniflow.app.engine.speech.model.FluencyAnalysisResult
import com.vaniflow.app.engine.speech.model.PronunciationEvidence
import com.vaniflow.app.engine.speech.model.SpeechQualityResult

/**
 * Deterministic pedagogical action determined by TutorDecisionEngine.
 */
enum class TutorAction {
    NORMAL_CONVERSATION,
    PASSIVE_CORRECTION,
    IMPORTANT_CORRECTION,
    CRITICAL_CORRECTION,
    ASK_RETRY,
    GIVE_SECOND_HINT,
    PRAISE_SUCCESS,
    PRACTICE_WEAK_CONCEPT,
    REUSE_VOCABULARY,
    LOWER_DIFFICULTY,
    MAINTAIN_DIFFICULTY,
    INCREASE_DIFFICULTY,
    ENCOURAGE_LEARNER,
    ASK_CLARIFYING_QUESTION,
    CONTINUE_SCENARIO,
    END_SESSION,
    NO_INTERVENTION
}

/**
 * Unified learner state snapshot aggregated from existing persistent and in-memory intelligence.
 */
data class TutorLearnerState(
    val cefrLevel: EstimatedLevel = EstimatedLevel.A2,
    val speakingConfidence: Float = 65.0f,
    val confidenceTrend: String = "Steady",
    val weakestConcepts: List<String> = emptyList(),
    val masteredConcepts: Set<String> = emptySet(),
    val conceptMasteryScores: Map<String, Int> = emptyMap(),
    val recentMistakes: List<EnglishError> = emptyList(),
    val successfulRetriesCount: Int = 0,
    val consecutiveFailures: Int = 0,
    val isStruggleBackoffActive: Boolean = false,
    val activeGoals: List<LearningGoal> = emptyList(),
    val vocabularyNeedingPractice: List<VocabularyMemory> = emptyList(),
    val latestQuality: SpeechQualityResult? = null,
    val latestFluency: FluencyAnalysisResult? = null,
    val latestPronunciation: PronunciationEvidence? = null,
    val sessionTurnCount: Int = 0,
    val sessionDurationMs: Long = 0L,
    val currentDifficulty: DifficultyLevel = DifficultyLevel.INTERMEDIATE,
    val isFairUseExceeded: Boolean = false,
    val isRetryActive: Boolean = false,
    val retryAttemptsCount: Int = 0,
    val activeRetryError: EnglishError? = null
)

/**
 * Output of the central TutorDecisionEngine detailing the chosen pedagogical decision.
 */
data class TutorDecision(
    val action: TutorAction,
    val reason: String,
    val shouldInterruptTurn: Boolean = false,
    val spokenInterventionText: String? = null,
    val suggestedTargetConcept: String? = null,
    val suggestedVocabularyToReuse: String? = null,
    val adaptiveDifficulty: DifficultyLevel = DifficultyLevel.INTERMEDIATE,
    val coachingDirective: String? = null
)
