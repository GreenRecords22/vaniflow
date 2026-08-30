package com.vaniflow.app.engine.learning.tutor.model

enum class DifficultyLevel(
    val displayLabel: String,
    val targetSentenceComplexity: String,
    val allowComplexVocabulary: Boolean,
    val correctionTolerance: String // "gentle", "moderate", "thorough"
) {
    BEGINNER(
        displayLabel = "Beginner",
        targetSentenceComplexity = "Short, simple sentences (5-10 words). Clear, basic vocabulary.",
        allowComplexVocabulary = false,
        correctionTolerance = "gentle"
    ),
    INTERMEDIATE(
        displayLabel = "Intermediate",
        targetSentenceComplexity = "Natural compound sentences (10-18 words). Everyday idioms and workplace vocabulary.",
        allowComplexVocabulary = true,
        correctionTolerance = "moderate"
    ),
    UPPER_INTERMEDIATE(
        displayLabel = "Upper Intermediate",
        targetSentenceComplexity = "Rich conversational phrasing with nuanced questions and diverse sentence structures.",
        allowComplexVocabulary = true,
        correctionTolerance = "moderate"
    ),
    ADVANCED(
        displayLabel = "Advanced",
        targetSentenceComplexity = "Fluent, sophisticated discourse with professional terminology and colloquial subtleties.",
        allowComplexVocabulary = true,
        correctionTolerance = "thorough"
    )
}
