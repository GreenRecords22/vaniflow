package com.vaniflow.app.engine

import com.vaniflow.app.domain.model.ConversationTurn
import com.vaniflow.app.domain.model.Correction
import com.vaniflow.app.domain.model.CorrectionCategory
import com.vaniflow.app.engine.learning.PronunciationScorer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PronunciationScorerTest {

    private val scorer = PronunciationScorer()

    @Test
    fun testScoreCalculationWithCorrections() {
        val turns = listOf(
            ConversationTurn(
                id = "1",
                sessionId = "s1",
                speaker = ConversationTurn.Speaker.AI,
                text = "Hello! What can I get for you?",
                timestamp = 1000L
            ),
            ConversationTurn(
                id = "2",
                sessionId = "s1",
                speaker = ConversationTurn.Speaker.USER,
                text = "I am living here since three years.",
                timestamp = 2000L,
                correction = Correction(
                    originalText = "I am living here since three years.",
                    suggestedText = "I have been living here for three years.",
                    explanation = "Use 'for' with duration.",
                    category = CorrectionCategory.GRAMMAR
                )
            ),
            ConversationTurn(
                id = "3",
                sessionId = "s1",
                speaker = ConversationTurn.Speaker.USER,
                text = "I would like to order one hot cappuccino please.",
                timestamp = 3000L
            )
        )

        val score = scorer.calculateSessionScore(turns, speakingTimeMinutes = 5)

        assertEquals(5, score.speakingTimeMinutes)
        assertTrue(score.fluencyScore in 60..100)
        assertTrue(score.grammarScore in 60..100)
        assertTrue(score.pronunciationScore in 60..100)
        assertTrue(score.vocabularyScore in 60..100)
        assertNotNull(score.strongestArea)
        assertNotNull(score.focusNext)
    }
}
