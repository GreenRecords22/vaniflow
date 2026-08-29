package com.vaniflow.app.engine.learning

import com.vaniflow.app.domain.model.Correction
import com.vaniflow.app.domain.model.CorrectionCategory
import com.vaniflow.app.domain.model.FeedbackImportance
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Selective Feedback Engine.
 * Analyzes user utterances and selectively surfaces high-value, natural English corrections
 * without interrupting conversational flow.
 */
interface FeedbackEngine {
    suspend fun analyzeUtterance(userInput: String): Correction?
}

@Singleton
class MockFeedbackEngine @Inject constructor() : FeedbackEngine {

    override suspend fun analyzeUtterance(userInput: String): Correction? {
        val lower = userInput.lowercase()
        return when {
            lower.contains("working in jaipur since") || lower.contains("working here since") -> {
                Correction(
                    originalText = userInput,
                    suggestedText = "I have been working in Jaipur for three years.",
                    explanation = "Use 'for' with a duration of time (e.g. 'for three years'), and 'since' with a specific starting point in time (e.g. 'since 2021').",
                    category = CorrectionCategory.GRAMMAR,
                    importance = FeedbackImportance.HIGH
                )
            }
            lower.contains("i am working in jaipur") -> {
                Correction(
                    originalText = userInput,
                    suggestedText = "I work in Jaipur.",
                    explanation = "Use Present Simple ('I work') when describing your permanent or regular job.",
                    category = CorrectionCategory.NATURAL_PHRASING,
                    importance = FeedbackImportance.MEDIUM
                )
            }
            lower.contains("i went to market yesterday") -> {
                Correction(
                    originalText = userInput,
                    suggestedText = "I went to the market yesterday.",
                    explanation = "Add the definite article 'the' before specific public places like 'the market'.",
                    category = CorrectionCategory.GRAMMAR,
                    importance = FeedbackImportance.LOW
                )
            }
            lower.contains("discuss about") -> {
                Correction(
                    originalText = userInput,
                    suggestedText = "discuss the project",
                    explanation = "'Discuss' is a transitive verb and takes a direct object without 'about'.",
                    category = CorrectionCategory.NATURAL_PHRASING,
                    importance = FeedbackImportance.MEDIUM
                )
            }
            else -> null
        }
    }
}
