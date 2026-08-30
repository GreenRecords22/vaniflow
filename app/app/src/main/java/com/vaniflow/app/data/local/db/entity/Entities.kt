package com.vaniflow.app.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.vaniflow.app.domain.model.SkillLevel

@Entity(tableName = "guest_profile")
data class GuestProfileEntity(
    @PrimaryKey val id: String = "guest_user",
    val name: String = "Sarah",
    val currentLevel: String = SkillLevel.INTERMEDIATE.name,
    val preferredCharacterId: String = "raya",
    val streakDays: Int = 5,
    val totalSpeakingMinutes: Int = 120,
    val fluencyProgressPercent: Int = 15,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey val id: String,
    val characterId: String,
    val scenarioId: String,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val speakingTimeMinutes: Int,
    val fluencyScore: Int,
    val grammarScore: Int,
    val pronunciationScore: Int,
    val vocabularyScore: Int,
    val strongestArea: String,
    val focusNext: String
)

@Entity(tableName = "conversation_turns")
data class ConversationTurnEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val speaker: String,
    val text: String,
    val timestamp: Long,
    val originalMistake: String? = null,
    val correctionSuggestion: String? = null,
    val correctionExplanation: String? = null,
    val correctionCategory: String? = null
)

@Entity(tableName = "saved_vocabulary")
data class SavedVocabularyEntity(
    @PrimaryKey val word: String,
    val phonetic: String,
    val partOfSpeech: String,
    val meaning: String,
    val example: String,
    val savedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "learner_profile")
data class LearnerProfileEntity(
    @PrimaryKey val id: String = "default_learner_profile",
    val estimatedLevel: String,
    val speakingConfidenceScore: Float,
    val totalUtterances: Int,
    val correctionsDelivered: Int,
    val successfulRetries: Int,
    val commonMistakesJson: String,
    val masteredConceptsJson: String,
    val conceptsNeedingPracticeJson: String,
    val recentCorrectionsJson: String,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "daily_usage")
data class DailyUsageEntity(
    @PrimaryKey val date: String, // Format: YYYY-MM-DD
    val speakingSeconds: Long,
    val inputTokens: Long,
    val outputTokens: Long,
    val totalRequests: Int,
    val cacheHits: Int = 0,
    val savedTokens: Long = 0L,
    val updatedAt: Long = System.currentTimeMillis()
)
