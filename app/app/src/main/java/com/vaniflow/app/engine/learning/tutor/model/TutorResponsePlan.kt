package com.vaniflow.app.engine.learning.tutor.model

/**
 * Structured pedagogical contract between TutorDecisionEngine and AI generation layer.
 *
 * Enforces teaching actions deterministically so the LLM verbalizes the pedagogical decision
 * without overriding tutor policy or inventing unprompted corrections.
 */
data class TutorResponsePlan(
    val tutorAction: TutorAction,
    val correctionRequired: Boolean = false,
    val correctionText: String? = null,
    val learnerRetryRequired: Boolean = false,
    val conversationIntent: String = "CONVERSATIONAL_RESPONSE",
    val contextSummary: String? = null,
    val targetConcept: String? = null,
    val characterStyle: String = "EMPATHIC_TUTOR",
    val generationInstruction: String = ""
)