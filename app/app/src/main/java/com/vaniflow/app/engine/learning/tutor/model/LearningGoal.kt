package com.vaniflow.app.engine.learning.tutor.model

import com.vaniflow.app.engine.learning.tutor.EnglishErrorCategory
import java.util.UUID

data class LearningGoal(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val description: String,
    val targetConceptId: String? = null,
    val targetCategory: EnglishErrorCategory? = null,
    val isAchieved: Boolean = false,
    val priority: Int = 1 // 1 (highest) to 3
)
