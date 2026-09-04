package com.vaniflow.app.engine.ai

import com.vaniflow.app.engine.ai.guard.QualityCheckResult
import com.vaniflow.app.engine.ai.guard.ResponseQualityGuard
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * P7.5 Live Conversation Multi-Scenario Benchmark Suite.
 * Simulates realistic multi-turn dialogues across diverse daily contexts.
 */
class P7_5_LiveConversationBenchmarkTest {

    private lateinit var guard: ResponseQualityGuard

    @Before
    fun setUp() {
        guard = ResponseQualityGuard()
        RepetitionGuard.reset()
    }

    @Test
    fun benchmark01_foodAndCooking() {
        val history = mutableListOf<AITurn>()
        val turns = listOf(
            "Do you know how to make butter chicken?" to "Yes, marinated chicken is cooked in a rich tomato, butter, and cream sauce with fragrant spices! Have you made it before?",
            "No, but I really want to learn." to "It's surprisingly straightforward once you have the spices ready—kasuri methi gives it that authentic restaurant aroma!",
            "Where can I buy those spices?" to "Most local grocery stores or Indian spice markets will have kasuri methi, garam masala, and Kashmiri chili powder."
        )

        for ((user, ai) in turns) {
            assertTrue(guard.validate(ai, user, history) is QualityCheckResult.Valid)
            history.add(AITurn(AITurn.Role.USER, user))
            history.add(AITurn(AITurn.Role.ASSISTANT, ai))
        }
    }

    @Test
    fun benchmark02_careerAndInterview() {
        val history = mutableListOf<AITurn>()
        val turns = listOf(
            "How should I answer 'What is your greatest weakness'?" to "Pick a genuine professional skill you are actively improving, and share the specific steps you're taking to master it.",
            "Is it good to mention public speaking?" to "Yes, public speaking is a great example—you can mention joining group presentations or speaking clubs to build confidence.",
            "That sounds practical, thanks!" to "You're very welcome! Would you like to practice framing your answer together?"
        )

        for ((user, ai) in turns) {
            assertTrue(guard.validate(ai, user, history) is QualityCheckResult.Valid)
            history.add(AITurn(AITurn.Role.USER, user))
            history.add(AITurn(AITurn.Role.ASSISTANT, ai))
        }
    }

    @Test
    fun benchmark03_scienceAndCuriosity() {
        val history = mutableListOf<AITurn>()
        val turns = listOf(
            "How do telescopes see distant galaxies?" to "Large mirrors or lenses collect and focus light from faint cosmic sources across billions of light years.",
            "What about the James Webb Space Telescope?" to "The James Webb telescope uses infrared sensors to look through cosmic dust and observe the earliest stars in our universe.",
            "That is mind-blowing!" to "It really is—astronomy gives us a breathtaking perspective on our place in the cosmos!"
        )

        for ((user, ai) in turns) {
            assertTrue(guard.validate(ai, user, history) is QualityCheckResult.Valid)
            history.add(AITurn(AITurn.Role.USER, user))
            history.add(AITurn(AITurn.Role.ASSISTANT, ai))
        }
    }
}
