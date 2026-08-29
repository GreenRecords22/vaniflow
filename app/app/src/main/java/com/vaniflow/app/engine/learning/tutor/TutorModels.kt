package com.vaniflow.app.engine.learning.tutor

/**
 * Severity level of a detected English learning issue.
 */
enum class CorrectionSeverity {
    CRITICAL,    // Meaning is unclear or communication is seriously broken
    IMPORTANT,   // Core grammar/tense/agreement error that should be learned
    MINOR,       // Small slip (missing article, minor preposition) not hurting meaning
    STYLE        // Grammatically acceptable, but more natural spoken phrasing exists
}

/**
 * Strategy for when to deliver a tutor correction during conversation.
 */
enum class CorrectionTiming {
    IMMEDIATE,       // Critical communication problem; address right away
    AFTER_UTTERANCE, // Important grammar mistake; weave gently after user utterance
    DELAYED,         // Minor/stylistic slip; batch or hold until naturally relevant
    NO_CORRECTION    // Completely correct sentence or confidence-protection hold
}

/**
 * Categories of English mistakes detected by the tutor engine.
 */
enum class EnglishErrorCategory {
    GRAMMAR,
    TENSE,
    ARTICLES,
    PREPOSITIONS,
    SUBJECT_VERB_AGREEMENT,
    SINGULAR_PLURAL,
    WORD_ORDER,
    WORD_CHOICE,
    SENTENCE_CONSTRUCTION,
    NATURAL_PHRASING,
    VOCABULARY,
    FLUENCY_FILLER
}

/**
 * An individual detected English mistake with rule explanation and suggestion.
 */
data class EnglishError(
    val originalText: String,
    val suggestedText: String,
    val category: EnglishErrorCategory,
    val severity: CorrectionSeverity,
    val explanation: String,
    val ruleIdentifier: String = "general_rule"
)

/**
 * Tutor's strategic decision on how to gently guide the learner.
 */
data class TutorCorrectionDecision(
    val hasError: Boolean,
    val detectedErrors: List<EnglishError> = emptyList(),
    val primarySeverity: CorrectionSeverity = CorrectionSeverity.MINOR,
    val timing: CorrectionTiming = CorrectionTiming.NO_CORRECTION,
    val gentleFeedback: String? = null,
    val correctedSentence: String? = null,
    val tutorExplanation: String? = null,
    val shouldRequestRetry: Boolean = false,
    val retryPrompt: String? = null
)

/**
 * Evaluation of a learner's retry attempt after receiving a gentle correction.
 */
data class RetryEvaluation(
    val originalError: EnglishError?,
    val retryUtterance: String,
    val isFixed: Boolean,
    val isPartiallyFixed: Boolean,
    val praiseFeedback: String,
    val masteryDelta: Float
)