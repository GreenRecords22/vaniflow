package com.vaniflow.app.engine.learning.tutor

import com.vaniflow.app.domain.model.Scenario
import com.vaniflow.app.engine.learning.tutor.model.LearningGoal
import com.vaniflow.app.engine.learning.tutor.model.MasteryState
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LearningGoalGenerator @Inject constructor() {

    fun generateGoalsForSession(
        learnerProfile: LearnerProfile,
        masteryList: List<MasteryState>,
        scenario: Scenario
    ): List<LearningGoal> {
        val goals = mutableListOf<LearningGoal>()

        // 1. Weakest concept goal
        val weakConcept = masteryList
            .filter { it.needsPractice || it.practicePriority >= 60 }
            .maxByOrNull { it.practicePriority }

        if (weakConcept != null) {
            val title = when (weakConcept.category) {
                EnglishErrorCategory.TENSE -> "Practice Past Tense"
                EnglishErrorCategory.SUBJECT_VERB_AGREEMENT -> "Practice Subject-Verb Agreement"
                EnglishErrorCategory.PREPOSITIONS -> "Refine Prepositions"
                EnglishErrorCategory.ARTICLES -> "Master Articles (a/an/the)"
                EnglishErrorCategory.WORD_ORDER -> "Natural Question Formation"
                EnglishErrorCategory.SINGULAR_PLURAL -> "Plurals & Countable Nouns"
                EnglishErrorCategory.NATURAL_PHRASING, EnglishErrorCategory.WORD_CHOICE -> "Natural Spoken Collocations"
                else -> "Conversational Fluency"
            }
            goals.add(
                LearningGoal(
                    title = title,
                    description = "Naturally use correct forms for ${weakConcept.category.name.lowercase().replace('_', ' ')}.",
                    targetConceptId = weakConcept.conceptId,
                    targetCategory = weakConcept.category,
                    priority = 1
                )
            )
        } else if (learnerProfile.conceptsNeedingPractice.isNotEmpty()) {
            val concept = learnerProfile.conceptsNeedingPractice.first()
            goals.add(
                LearningGoal(
                    title = "Improve $concept",
                    description = "Focus on speaking smoothly with correct $concept phrasing.",
                    targetConceptId = concept,
                    priority = 1
                )
            )
        }

        // 2. Scenario-specific goal
        val scenarioGoal = when {
            scenario.id.contains("interview", ignoreCase = true) ->
                LearningGoal(title = "Professional Articulation", description = "Express professional experience concisely and confidently.", priority = 2)
            scenario.id.contains("travel", ignoreCase = true) || scenario.id.contains("airport", ignoreCase = true) ->
                LearningGoal(title = "Travel Situations", description = "Ask for directions, schedules, and details naturally.", priority = 2)
            scenario.id.contains("coffee", ignoreCase = true) || scenario.id.contains("restaurant", ignoreCase = true) ->
                LearningGoal(title = "Everyday Requests", description = "Order items and engage in friendly small talk with ease.", priority = 2)
            else ->
                LearningGoal(title = "Fluency & Response Flow", description = "Keep conversations flowing with natural conversational turns.", priority = 2)
        }
        goals.add(scenarioGoal)

        return goals.take(3)
    }
}
