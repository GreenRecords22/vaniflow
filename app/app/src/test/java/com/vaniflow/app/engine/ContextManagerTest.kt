package com.vaniflow.app.engine

import com.vaniflow.app.domain.model.SkillLevel
import com.vaniflow.app.engine.ai.AITurn
import com.vaniflow.app.engine.ai.ContextManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextManagerTest {

    @Test
    fun testBuildSystemPromptContainsAllParameters() {
        val context = ContextManager.ConversationContext(
            characterName = "Raya",
            personalityPrompt = "You are Raya, a warm and encouraging conversation coach.",
            scenarioTitle = "Order Coffee",
            scenarioPrompt = "User is practicing ordering drinks.",
            targetVocabulary = listOf("cappuccino", "decaf"),
            userLevel = SkillLevel.BEGINNER
        )

        val prompt = ContextManager.buildSystemPrompt(context)
        assertTrue(prompt.contains("Raya"))
        assertTrue(prompt.contains("Order Coffee"))
        assertTrue(prompt.contains("cappuccino, decaf"))
        assertTrue(prompt.contains("1 to 2 short conversational sentences"))
    }

    @Test
    fun testHistoryCompactionRetainsLastNturns() {
        val fullHistory = (1..10).map { i ->
            AITurn(
                role = if (i % 2 == 0) AITurn.Role.ASSISTANT else AITurn.Role.USER,
                content = "Turn number $i"
            )
        }

        val compacted = ContextManager.compactHistory(fullHistory, maxTurns = 4)
        assertEquals(4, compacted.size)
        assertEquals("Turn number 7", compacted[0].content)
        assertEquals("Turn number 10", compacted[3].content)
    }

    @Test
    fun testTokenEstimation() {
        val text = "Hello world! This is a test sentence."
        val count = ContextManager.estimateTokenCount(text)
        assertTrue("Estimated token count should be proportional to length", count in 5..15)
        assertEquals(0, ContextManager.estimateTokenCount(""))
    }
}
