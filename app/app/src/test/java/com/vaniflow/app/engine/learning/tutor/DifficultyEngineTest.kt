package com.vaniflow.app.engine.learning.tutor

import com.vaniflow.app.engine.learning.tutor.model.DifficultyLevel
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class DifficultyEngineTest {

    private lateinit var engine: DifficultyEngine

    @Before
    fun setup() {
        engine = DifficultyEngine()
    }

    @Test
    fun `beginner base level corresponds to Beginner difficulty`() {
        val diff = engine.determineDifficulty(
            estimatedLevel = EstimatedLevel.A1,
            speakingConfidence = 70f,
            successfulRetriesCount = 2,
            totalUtterances = 10
        )
        assertEquals(DifficultyLevel.BEGINNER, diff)
    }

    @Test
    fun `low confidence drops difficulty level to protect learner`() {
        val diff = engine.determineDifficulty(
            estimatedLevel = EstimatedLevel.B2,
            speakingConfidence = 35f, // Struggling learner
            successfulRetriesCount = 1,
            totalUtterances = 10
        )
        assertEquals("Difficulty must drop to Intermediate when confidence is low", DifficultyLevel.INTERMEDIATE, diff)
    }

    @Test
    fun `high consistency nudges difficulty up`() {
        val diff = engine.determineDifficulty(
            estimatedLevel = EstimatedLevel.B1,
            speakingConfidence = 90f,
            successfulRetriesCount = 18,
            totalUtterances = 20
        )
        assertEquals("Difficulty should elevate to Upper Intermediate on high consistency", DifficultyLevel.UPPER_INTERMEDIATE, diff)
    }
}
