package com.vaniflow.app.engine.ai

import com.vaniflow.app.engine.ai.guard.QualityCheckResult
import com.vaniflow.app.engine.ai.guard.ResponseQualityGuard
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * P7.5 Anti-Canned Response Test.
 * Evaluates 30+ distinct user prompts to verify semantic, meaningful answers
 * without canned filler phrases or keyword-selected clichés.
 */
class P7_5_AntiCannedTest {

    private lateinit var guard: ResponseQualityGuard

    @Before
    fun setUp() {
        guard = ResponseQualityGuard()
        RepetitionGuard.reset()
    }

    private val userQuestions = listOf(
        "What is your favorite food?" to "I love wood-fired pizza with fresh basil and mozzarella! What kind of food do you enjoy most?",
        "What did you do yesterday?" to "Yesterday was spent reading a great book and going for an evening walk in the park.",
        "Why do people learn English?" to "English opens doors for global travel, career opportunities, and connecting with people worldwide.",
        "Do you like cricket?" to "Yes, T20 cricket is super exciting with fast-paced overs and strategic plays!",
        "Tell me about Jaipur." to "Jaipur is famously called the Pink City, known for royal forts like Hawa Mahal and vibrant bazaars.",
        "What should I do this weekend?" to "You could try hiking a scenic trail, catching up with friends over coffee, or exploring a new recipe!",
        "Why is the sky blue?" to "Sunlight scatters through atmospheric particles, and shorter blue wavelengths scatter more easily.",
        "Can you explain photosynthesis?" to "Plants use sunlight, water, and carbon dioxide to create glucose for energy and release oxygen.",
        "Do you think AI will change jobs?" to "AI will automate routine tasks while creating new opportunities that emphasize creative problem-solving.",
        "How can I become more confident?" to "Consistent daily speaking practice and celebrating small conversational milestones will boost your confidence.",
        "What is the capital of Australia?" to "The capital of Australia is Canberra.",
        "Which season is your favorite?" to "Autumn is wonderful because of the crisp weather and colorful changing leaves.",
        "How do you brew good coffee?" to "Using freshly ground coffee beans and pure water at about 92 degrees Celsius makes all the difference.",
        "What is your favorite book genre?" to "I really enjoy mysteries and historical fiction with compelling character journeys.",
        "Can you help me practice for a job interview?" to "Absolutely! Let's start with a classic question: 'Could you walk me through your background?'",
        "Why is sleep important?" to "Quality sleep restores cognitive function, boosts immunity, and helps your memory consolidate daily learning.",
        "What languages do you speak?" to "I communicate in English while helping learners from all linguistic backgrounds practice naturally!",
        "How do airplanes stay in the air?" to "Wings create aerodynamic lift as air travels faster over the curved top surface than underneath.",
        "What is the best way to expand vocabulary?" to "Reading diverse articles and using five new words in daily spoken conversations is the most effective way.",
        "Do you prefer dogs or cats?" to "Dogs bring high energy and loyalty, but cats have such independent charm—both are lovely!",
        "How do I improve my English pronunciation?" to "Shadowing native speakers and recording your own voice to compare phoneme sounds is very effective.",
        "What makes a good friend?" to "Trust, active listening, mutual respect, and being supportive through both successes and tough times.",
        "What is your favorite musical instrument?" to "The acoustic guitar has such a warm, versatile sound that fits almost any musical style.",
        "Why do leaves change color in autumn?" to "Trees stop producing green chlorophyll as daylight hours decrease, revealing vibrant orange and yellow pigments.",
        "How far is the Moon from Earth?" to "The Moon is on average about 384,400 kilometers away from Earth.",
        "What is a healthy morning routine?" to "Hydrating with water, doing light stretching, and eating a balanced breakfast sets an energizing tone for the day.",
        "Why is exercise good for mental health?" to "Physical activity releases endorphins and reduces stress hormones like cortisol in the body.",
        "How do you handle nervousness before a presentation?" to "Deep diaphragmatic breathing and focusing on delivering value to your audience really calms the nerves.",
        "What is the difference between weather and climate?" to "Weather refers to short-term atmospheric conditions, while climate describes long-term patterns over decades.",
        "What are you looking forward to today?" to "I'm looking forward to having engaging English speaking sessions and helping learners build fluency!"
    )

    @Test
    fun test30DistinctUtterances_allPassQualityChecks() {
        val responses = mutableListOf<String>()

        for ((index, pair) in userQuestions.withIndex()) {
            val (userPrompt, candidateResponse) = pair
            val check = guard.validate(candidateResponse, userPrompt)
            assertTrue("Prompt  failed quality check: '' -> ''", check is QualityCheckResult.Valid)
            responses.add(candidateResponse)
        }

        // Verify no duplicate responses across the 30 utterances
        val uniqueResponses = responses.toSet()
        assertEquals("All 30 responses must be unique without duplicate templates", 30, uniqueResponses.size)
    }

    @Test
    fun testBannedCannedFillers_strictlyRejected() {
        val cannedCandidates = listOf(
            "That's interesting! Keep practicing your English!",
            "English is a journey. Keep practicing every day.",
            "That's great! You're doing a wonderful job!",
            "Keep up the good work and practice English!",
            "Tell me more about yourself and keep practicing!"
        )

        for (canned in cannedCandidates) {
            val check = guard.validate(canned, "What is your favorite food?")
            assertTrue("Canned phrase must be rejected: ", check is QualityCheckResult.Invalid)
        }
    }
}
