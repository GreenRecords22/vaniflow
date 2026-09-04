package com.vaniflow.app.engine.ai

import com.vaniflow.app.domain.model.SkillLevel
import com.vaniflow.app.engine.ai.guard.QualityCheckResult
import com.vaniflow.app.engine.ai.guard.ResponseQualityGuard
import com.vaniflow.app.engine.ai.prompt.ConversationPromptBuilder
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * P7.5 Context Continuity & Topic Switching Acceptance Suite.
 * Tests pronoun resolution, entity tracking across turns, and rapid topic switching.
 */
class P7_5_ContextContinuityTest {

    private lateinit var guard: ResponseQualityGuard

    @Before
    fun setUp() {
        guard = ResponseQualityGuard()
        RepetitionGuard.reset()
    }

    @Test
    fun test01_multiTurnBrotherJaipurDelhiCricket() {
        val history = mutableListOf<AITurn>()

        // Turn 1: "My brother lives in Jaipur."
        val turn1User = "My brother lives in Jaipur."
        val turn1AI = "Jaipur is a wonderful city! What does your brother do there?"
        assertTrue(guard.validate(turn1AI, turn1User, history) is QualityCheckResult.Valid)
        history.add(AITurn(AITurn.Role.USER, turn1User))
        history.add(AITurn(AITurn.Role.ASSISTANT, turn1AI))

        // Turn 2: "He works at a hospital." -> 'He' refers to brother
        val turn2User = "He works at a hospital."
        val turn2AI = "Working in healthcare is so rewarding! Is he a doctor or a specialist?"
        assertTrue(guard.validate(turn2AI, turn2User, history) is QualityCheckResult.Valid)
        history.add(AITurn(AITurn.Role.USER, turn2User))
        history.add(AITurn(AITurn.Role.ASSISTANT, turn2AI))

        // Turn 3: "He wants to travel to Delhi next month." -> Maintains brother + Delhi context
        val turn3User = "He wants to travel to Delhi next month."
        val turn3AI = "Delhi has great medical conferences and sights. Is he going for work or a vacation?"
        assertTrue(guard.validate(turn3AI, turn3User, history) is QualityCheckResult.Valid)
        history.add(AITurn(AITurn.Role.USER, turn3User))
        history.add(AITurn(AITurn.Role.ASSISTANT, turn3AI))

        // Turn 4: "Why do you think he wants to go there?" -> 'there' = Delhi, 'he' = brother
        val turn4User = "Why do you think he wants to go there?"
        val turn4AI = "Perhaps to explore the rich history, visit friends, or attend professional workshops!"
        assertTrue(guard.validate(turn4AI, turn4User, history) is QualityCheckResult.Valid)
        history.add(AITurn(AITurn.Role.USER, turn4User))
        history.add(AITurn(AITurn.Role.ASSISTANT, turn4AI))

        // Turn 5: Abrupt topic switch to cricket -> "Anyway, what do you think about cricket?"
        val turn5User = "Anyway, what do you think about cricket?"
        val turn5AI = "Cricket is such a thrilling sport, especially during intense tournament matches! Do you play or just watch?"
        assertTrue(guard.validate(turn5AI, turn5User, history) is QualityCheckResult.Valid)
    }

    @Test
    fun test02_rapidDomainTransitions() {
        val domains = listOf(
            "I love spicy Indian curries like paneer butter masala." to "Paneer butter masala with warm naan is absolute comfort food!",
            "I'm preparing for a software engineer interview next week." to "That's exciting! What tech stack or topics are you focusing on for the interview?",
            "My sister just adopted a golden retriever puppy." to "Puppies bring so much joy! What name did your sister pick for him?",
            "Do you think India will win the next World Cup?" to "With strong batting and bowling depth, they definitely have a fantastic chance!",
            "I want to visit Switzerland during the winter." to "The snow-covered Alps in Switzerland look like a postcard! Are you interested in skiing?"
        )

        val history = mutableListOf<AITurn>()
        for ((userMsg, aiMsg) in domains) {
            val check = guard.validate(aiMsg, userMsg, history)
            assertTrue("Domain transition failed for: ", check is QualityCheckResult.Valid)
            history.add(AITurn(AITurn.Role.USER, userMsg))
            history.add(AITurn(AITurn.Role.ASSISTANT, aiMsg))
        }
    }

    @Test
    fun test03_promptBuilderEncodesPronounResolutionRule() {
        val history = listOf(
            AITurn(AITurn.Role.USER, "My brother lives in Jaipur."),
            AITurn(AITurn.Role.ASSISTANT, "Jaipur is lovely! What does he do?")
        )
        val prompt = ConversationPromptBuilder.buildRuntimePrompt(
            characterName = "Raya",
            personalityPrompt = "Energetic",
            scenarioTitle = "General",
            scenarioPrompt = "Chat",
            userLevel = SkillLevel.INTERMEDIATE,
            history = history,
            userInput = "He works at a hospital."
        )
        assertTrue(prompt.contains("resolve them from the CONVERSATION HISTORY"))
    }
}
