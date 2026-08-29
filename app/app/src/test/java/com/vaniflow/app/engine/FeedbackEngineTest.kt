package com.vaniflow.app.engine

import com.vaniflow.app.domain.model.CorrectionCategory
import com.vaniflow.app.engine.learning.MockFeedbackEngine
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class FeedbackEngineTest {

    private val feedbackEngine = MockFeedbackEngine()

    @Test
    fun testSelectiveCorrectionForPresentContinuousHabit() = runTest {
        val correction = feedbackEngine.analyzeUtterance("I am working in Jaipur")
        assertNotNull(correction)
        assertEquals("I work in Jaipur.", correction?.suggestedText)
        assertEquals(CorrectionCategory.NATURAL_PHRASING, correction?.category)
    }

    @Test
    fun testCorrectionForSinceDuration() = runTest {
        val correction = feedbackEngine.analyzeUtterance("I am working in Jaipur since three years.")
        assertNotNull(correction)
        assertEquals("I have been working in Jaipur for three years.", correction?.suggestedText)
        assertEquals(CorrectionCategory.GRAMMAR, correction?.category)
    }

    @Test
    fun testNoCorrectionForNaturalSentence() = runTest {
        val correction = feedbackEngine.analyzeUtterance("I really enjoy drinking coffee in the morning.")
        assertNull(correction)
    }
}
