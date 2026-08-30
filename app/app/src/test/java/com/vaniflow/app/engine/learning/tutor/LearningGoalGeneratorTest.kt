package com.vaniflow.app.engine.learning.tutor

import com.vaniflow.app.domain.model.Scenario
import com.vaniflow.app.engine.learning.tutor.model.MasteryState
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class LearningGoalGeneratorTest {

    private lateinit var generator: LearningGoalGenerator

    @Before
    fun setup() {
        generator = LearningGoalGenerator()
    }

    @Test
    fun `generates targeted goal for weakest concept`() {
        val profile = LearnerProfile()
        val mastery = listOf(
            MasteryState(
                conceptId = "past_buyed",
                category = EnglishErrorCategory.TENSE,
                masteryScore = 25,
                practicePriority = 85
            ),
            MasteryState(
                conceptId = "article_a_an",
                category = EnglishErrorCategory.ARTICLES,
                masteryScore = 80,
                practicePriority = 20
            )
        )

        val scenario = Scenario(
            id = "free_talk",
            title = "Casual Small Talk",
            description = "Chat freely",
            category = "Daily Life",
            difficulty = com.vaniflow.app.domain.model.SkillLevel.BEGINNER,
            estimatedMinutes = 5,
            initialMessage = "Hey there!",
            systemPrompt = "You are a friendly companion."
        )

        val goals = generator.generateGoalsForSession(profile, mastery, scenario)
        assertTrue("Goals must not be empty", goals.isNotEmpty())
        val primaryGoal = goals.first()
        assertEquals("Practice Past Tense", primaryGoal.title)
        assertEquals("past_buyed", primaryGoal.targetConceptId)
    }

    @Test
    fun `generates scenario relevant goal for job interview`() {
        val profile = LearnerProfile()
        val scenario = Scenario(
            id = "job_interview",
            title = "Job Interview",
            description = "Interview practice",
            category = "Work",
            difficulty = com.vaniflow.app.domain.model.SkillLevel.ADVANCED,
            estimatedMinutes = 10,
            initialMessage = "Tell me about yourself.",
            systemPrompt = "You are an interviewer."
        )

        val goals = generator.generateGoalsForSession(profile, emptyList(), scenario)
        assertTrue("Should include scenario goal", goals.any { it.title.contains("Professional Articulation") })
    }
}
