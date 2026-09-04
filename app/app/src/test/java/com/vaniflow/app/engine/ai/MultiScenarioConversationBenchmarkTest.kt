package com.vaniflow.app.engine.ai

import com.vaniflow.app.domain.model.SkillLevel
import com.vaniflow.app.engine.ai.prompt.ConversationPromptBuilder
import com.vaniflow.app.engine.learning.tutor.EnglishCorrectionEngine
import com.vaniflow.app.engine.learning.tutor.TutorDecisionEngine
import com.vaniflow.app.engine.learning.tutor.model.TutorAction
import com.vaniflow.app.engine.learning.tutor.model.TutorLearnerState
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Section 19 Deterministic Benchmark:
 * Evaluates 6 core conversation scenarios (5-10 turns each):
 * 1. Daily life
 * 2. Travel
 * 3. Workplace
 * 4. Food
 * 5. Interview
 * 6. Free conversation
 */
class MultiScenarioConversationBenchmarkTest {

    private lateinit var correctionEngine: EnglishCorrectionEngine
    private lateinit var decisionEngine: TutorDecisionEngine

    @Before
    fun setUp() {
        correctionEngine = EnglishCorrectionEngine()
        decisionEngine = TutorDecisionEngine()
    }

    @Test
    fun benchmarkScenario1_DailyLife() {
        val turns = listOf(
            "Hi Raya, how are you doing today?",
            "I woke up early and went for a jog in the park.",
            "I am working here since five years.", // mistake
            "I usually make coffee and read the news.",
            "What do you usually do in the morning?"
        )

        var correctionsCount = 0
        turns.forEach { turn ->
            val dec = correctionEngine.analyzeUtterance(turn)
            if (dec.hasError) correctionsCount++
        }

        // Exactly 1 correction for "since five years"
        assertEquals(1, correctionsCount)
    }

    @Test
    fun benchmarkScenario2_Travel() {
        val turns = listOf(
            "I love traveling to new places.",
            "I went Jaipur yesterday.", // mistake: missing "to"
            "I visited the grand palace and took photos.",
            "Have you ever traveled to the mountains?",
            "I want to visit Leh Ladakh next summer."
        )

        var correctionsCount = 0
        turns.forEach { turn ->
            val dec = correctionEngine.analyzeUtterance(turn)
            if (dec.hasError) correctionsCount++
        }

        assertEquals(1, correctionsCount)
    }

    @Test
    fun benchmarkScenario3_Workplace() {
        val turns = listOf(
            "I am preparing a presentation for my manager.",
            "We have many informations to present.", // mistake: uncountable noun
            "Our team completed the sprint on time.",
            "He don't agree with the proposed timeline.", // mistake: subject verb agreement
            "We will discuss the requirements in our meeting tomorrow."
        )

        var correctionsCount = 0
        turns.forEach { turn ->
            val dec = correctionEngine.analyzeUtterance(turn)
            if (dec.hasError) correctionsCount++
        }

        assertEquals(2, correctionsCount)
    }

    @Test
    fun benchmarkScenario4_Food() {
        val turns = listOf(
            "What is your favorite food?",
            "I really enjoy eating spicy South Indian food.",
            "Yesterday I go market to buy vegetables.", // mistake: past tense
            "I cooked dinner for my whole family.",
            "Do you prefer sweet or savory dishes?"
        )

        var correctionsCount = 0
        turns.forEach { turn ->
            val dec = correctionEngine.analyzeUtterance(turn)
            if (dec.hasError) correctionsCount++
        }

        assertEquals(1, correctionsCount)
    }

    @Test
    fun benchmarkScenario5_Interview() {
        val turns = listOf(
            "Good morning! I am interviewing for the software developer role.",
            "I have two brother who also work in technology.", // mistake: singular brother
            "I graduated from college two years ago.",
            "I have experience building cloud services with Kotlin and Java.",
            "Could you tell me more about the team culture?"
        )

        var correctionsCount = 0
        turns.forEach { turn ->
            val dec = correctionEngine.analyzeUtterance(turn)
            if (dec.hasError) correctionsCount++
        }

        assertEquals(1, correctionsCount)
    }

    @Test
    fun benchmarkScenario6_FreeConversation() {
        val turns = listOf(
            "It is raining heavily outside today.",
            "I am sitting near the window with a cup of tea.",
            "Rainy days always make me feel relaxed.",
            "I love listening to acoustic guitar music when it rains.",
            "What is the weather like where you are?"
        )

        var correctionsCount = 0
        turns.forEach { turn ->
            val dec = correctionEngine.analyzeUtterance(turn)
            if (dec.hasError) correctionsCount++
        }

        // All 5 turns are grammatically clean -> 0 fake corrections!
        assertEquals(0, correctionsCount)
    }
}
