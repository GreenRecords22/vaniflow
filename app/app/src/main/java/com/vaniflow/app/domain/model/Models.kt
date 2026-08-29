package com.vaniflow.app.domain.model

import kotlinx.serialization.Serializable

/**
 * Domain models for VaniFlow
 */

@Serializable
enum class SkillLevel {
    BEGINNER,
    ELEMENTARY,
    INTERMEDIATE,
    UPPER_INTERMEDIATE,
    ADVANCED
}

@Serializable
enum class FeedbackImportance {
    LOW,
    MEDIUM,
    HIGH
}

@Serializable
enum class CorrectionCategory {
    GRAMMAR,
    NATURAL_PHRASING,
    VOCABULARY,
    PRONUNCIATION,
    FLUENCY
}

@Serializable
enum class ConversationState {
    IDLE,
    LISTENING,
    USER_SPEAKING,
    TRANSCRIBING,
    THINKING,
    AI_SPEAKING,
    INTERRUPTED,
    ERROR,
    SESSION_COMPLETE
}

@Serializable
data class Character(
    val id: String,
    val name: String,
    val personality: String,
    val level: SkillLevel,
    val voiceId: String,
    val speakingRate: Float = 1.0f,
    val correctionStyle: String = "gentle",
    val avatarUrl: String? = null,
    val systemPrompt: String,
    val isDefault: Boolean = false
)

@Serializable
data class Scenario(
    val id: String,
    val title: String,
    val description: String,
    val category: String,
    val difficulty: SkillLevel,
    val estimatedMinutes: Int,
    val initialMessage: String,
    val systemPrompt: String,
    val targetVocabulary: List<String> = emptyList()
)

@Serializable
data class Correction(
    val originalText: String,
    val suggestedText: String,
    val explanation: String,
    val category: CorrectionCategory,
    val importance: FeedbackImportance = FeedbackImportance.MEDIUM
)

@Serializable
data class ConversationTurn(
    val id: String,
    val sessionId: String,
    val speaker: Speaker,
    val text: String,
    val timestamp: Long,
    val correction: Correction? = null,
    val audioDurationMs: Long = 0
) {
    enum class Speaker {
        USER,
        AI
    }
}

@Serializable
data class SessionScore(
    val speakingTimeMinutes: Int,
    val fluencyScore: Int,
    val grammarScore: Int,
    val pronunciationScore: Int,
    val vocabularyScore: Int,
    val strongestArea: String,
    val focusNext: String,
    val focusNextExplanation: String
)

@Serializable
data class GuestProfile(
    val id: String = "guest_user",
    val name: String = "Sarah",
    val currentLevel: SkillLevel = SkillLevel.INTERMEDIATE,
    val preferredCharacterId: String = "raya",
    val streakDays: Int = 5,
    val totalSpeakingMinutes: Int = 120,
    val fluencyProgressPercent: Int = 15
)
