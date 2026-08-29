package com.vaniflow.app.engine

import com.vaniflow.app.domain.model.CorrectionCategory
import com.vaniflow.app.engine.learning.GrammarEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GrammarEngineTest {

    private val grammarEngine = GrammarEngine()

    @Test
    fun testDurationPrepositionCorrection() {
        val result = grammarEngine.analyze("I have been working here since three years.")
        assertNotNull(result)
        assertEquals("I have been working here for three years.", result!!.suggestedText)
        assertEquals(CorrectionCategory.GRAMMAR, result.category)
    }

    @Test
    fun testStativeVerbHavingCorrection() {
        val result = grammarEngine.analyze("I am having two brothers.")
        assertNotNull(result)
        assertEquals("I have two brothers.", result!!.suggestedText)
        assertEquals(CorrectionCategory.NATURAL_PHRASING, result.category)
    }

    @Test
    fun testStativeVerbKnowingCorrection() {
        val result = grammarEngine.analyze("I am knowing this place very well.")
        assertNotNull(result)
        assertEquals("I know this place very well.", result!!.suggestedText)
    }

    @Test
    fun testRedundantPrepositionDiscussAbout() {
        val result = grammarEngine.analyze("Let us discuss about the project architecture.")
        assertNotNull(result)
        assertEquals("Let us discuss the project architecture.", result!!.suggestedText)
    }

    @Test
    fun testRedundantPrepositionOrderFor() {
        val result = grammarEngine.analyze("I would like to order for a coffee please.")
        assertNotNull(result)
        assertEquals("I would like to order a coffee please.", result!!.suggestedText)
    }

    @Test
    fun testRedundantWordReturnBack() {
        val result = grammarEngine.analyze("I will return back tomorrow.")
        assertNotNull(result)
        assertEquals("I will return tomorrow.", result!!.suggestedText)
    }

    @Test
    fun testPastTenseAuxiliaryCorrection() {
        val result = grammarEngine.analyze("Yesterday I did not went to office.")
        assertNotNull(result)
        assertEquals("Yesterday I did not go to office.", result!!.suggestedText)
    }

    @Test
    fun testSubjectVerbAgreementOneOfMyFriends() {
        val result = grammarEngine.analyze("One of my friend lives in Bangalore.")
        assertNotNull(result)
        assertEquals("One of my friends lives in Bangalore.", result!!.suggestedText)
    }

    @Test
    fun testCorrectSentenceReturnsNull() {
        val result = grammarEngine.analyze("I have been living in Bangalore for four years.")
        assertNull("Flawless sentence should receive no correction", result)
    }
}
