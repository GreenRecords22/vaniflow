package com.vaniflow.app.engine.learning.tutor

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * High-level estimated CEFR English speaking proficiency level.
 */
enum class EstimatedLevel(val displayLabel: String, val description: String) {
    A1("Beginner (A1)", "Basic vocabulary and simple phrases"),
    A2("Elementary (A2)", "Everyday conversational topics and familiar routines"),
    B1("Intermediate (B1)", "Expressing opinions, describing past events and plans"),
    B2("Upper-Intermediate (B2)", "Fluent spontaneous conversation on varied subjects"),
    C1("Advanced (C1)", "Articulate, nuanced professional and academic expression")
}

/**
 * Bounded learner profile tracking speaking confidence, mistake history, and concept mastery.
 */
data class LearnerProfile(
    var estimatedLevel: EstimatedLevel = EstimatedLevel.A2,
    var speakingConfidenceScore: Float = 65.0f, // 0..100
    var totalUtterances: Int = 0,
    var correctionsDelivered: Int = 0,
    var successfulRetries: Int = 0,
    val commonMistakes: ConcurrentHashMap<String, Int> = ConcurrentHashMap(),
    val masteredConcepts: MutableSet<String> = ConcurrentHashMap.newKeySet(),
    val conceptsNeedingPractice: MutableSet<String> = ConcurrentHashMap.newKeySet(),
    val recentCorrections: CopyOnWriteArrayList<EnglishError> = CopyOnWriteArrayList()
) {
    fun recordMistake(ruleId: String, category: EnglishErrorCategory) {
        commonMistakes.compute(ruleId) { _, count -> (count ?: 0) + 1 }
        val categoryKey = category.name.lowercase()
        conceptsNeedingPractice.add(categoryKey)
        masteredConcepts.remove(ruleId)
    }

    fun recordSuccessfulRetry(ruleId: String, category: EnglishErrorCategory) {
        successfulRetries++
        speakingConfidenceScore = (speakingConfidenceScore + 1.5f).coerceAtMost(100f)
        masteredConcepts.add(ruleId)
        val categoryKey = category.name.lowercase()
        val currentCount = commonMistakes[ruleId] ?: 0
        if (currentCount <= 2) {
            conceptsNeedingPractice.remove(categoryKey)
        }
    }

    fun getCompactSummary(): String {
        val weakList = conceptsNeedingPractice.take(3).joinToString(", ")
        val weakText = if (weakList.isNotBlank()) "Needs practice on: $weakList." else "Grammar flow is solid."
        return "Learner Level: ${estimatedLevel.displayLabel}. Speaking Confidence: ${speakingConfidenceScore.toInt()}%. $weakText"
    }

    fun copyProfile(): LearnerProfile {
        val copy = LearnerProfile(
            estimatedLevel = this.estimatedLevel,
            speakingConfidenceScore = this.speakingConfidenceScore,
            totalUtterances = this.totalUtterances,
            correctionsDelivered = this.correctionsDelivered,
            successfulRetries = this.successfulRetries
        )
        copy.commonMistakes.putAll(this.commonMistakes)
        copy.masteredConcepts.addAll(this.masteredConcepts)
        copy.conceptsNeedingPractice.addAll(this.conceptsNeedingPractice)
        copy.recentCorrections.addAll(this.recentCorrections)
        return copy
    }
}