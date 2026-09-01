package com.vaniflow.app.engine.learning.tutor.model

import com.vaniflow.app.engine.learning.tutor.CorrectionSeverity
import com.vaniflow.app.engine.learning.tutor.EnglishErrorCategory
import java.util.UUID

enum class LearningEventType {
    CORRECTION,
    SUCCESSFUL_RETRY,
    FAILED_RETRY,
    MASTERY_GAIN,
    MASTERY_DECAY,
    VOCABULARY_LEARNED,
    VOCABULARY_REUSED,
    FLUENCY_EVENT,
    FLUENCY_OBSERVED,
    PRONUNCIATION_OBSERVED,
    PRONUNCIATION_IMPROVEMENT,
    SESSION_COMPLETED,
    CONCEPT_PRACTICED
}

data class LearningEvent(
    val id: String = UUID.randomUUID().toString(),
    val type: LearningEventType,
    val conceptId: String,
    val category: EnglishErrorCategory,
    val severity: CorrectionSeverity = CorrectionSeverity.MINOR,
    val originalUtterance: String? = null,
    val correctedForm: String? = null,
    val isSuccess: Boolean = true,
    val sessionId: String? = null,
    val confidenceImpact: Float = 0f,
    val timestampEpochMs: Long = System.currentTimeMillis()
)
