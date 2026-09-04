package com.vaniflow.app.engine.ai

import com.vaniflow.app.engine.learning.tutor.EnglishCorrectionEngine
import com.vaniflow.app.engine.learning.tutor.EnglishErrorCategory
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * P7.5 Real English Correction Acceptance Test.
 * Verifies that incorrect learner utterances are accurately corrected
 * and that grammatically correct sentences are NEVER falsely flagged.
 */
class P7_5_CorrectionAcceptanceTest {

    private lateinit var correctionEngine: EnglishCorrectionEngine

    @Before
    fun setUp() {
        correctionEngine = EnglishCorrectionEngine()
    }

    // 1. "I go Jaipur yesterday." (Past-tense + missing preposition)
    @Test
    fun test01_pastTenseLocation_incorrectCaught() {
        val utterance = "I go Jaipur yesterday."
        val res = correctionEngine.analyzeUtterance(utterance)
        assertTrue("Error should be detected in: ", res.hasError)
        val corrected = res.correctedSentence ?: ""
        assertTrue("Expected 'went to Jaipur' in ''", corrected.contains("went to Jaipur", ignoreCase = true))
    }

    // 2. "I am living here since five years." (Duration with since -> for)
    @Test
    fun test02_livingSinceDuration_incorrectCaught() {
        val utterance = "I am living here since five years."
        val res = correctionEngine.analyzeUtterance(utterance)
        assertTrue("Error should be detected in: ", res.hasError)
        val corrected = res.correctedSentence ?: ""
        assertTrue("Expected 'for 5 years' or 'for five years' in ''", corrected.contains("for five years", ignoreCase = true) || corrected.contains("for 5 years", ignoreCase = true))
    }

    // 3. "I arrived on the morning." (Preposition on -> in the morning)
    @Test
    fun test03_arrivedOnMorning_incorrectCaught() {
        val utterance = "I arrived on the morning."
        val res = correctionEngine.analyzeUtterance(utterance)
        assertTrue("Error should be detected in: ", res.hasError)
        val corrected = res.correctedSentence ?: ""
        assertTrue("Expected 'in the morning' in ''", corrected.contains("in the morning", ignoreCase = true))
    }

    // 4. "Yesterday I meet my friend." (Past tense meet -> met)
    @Test
    fun test04_yesterdayMeet_incorrectCaught() {
        val utterance = "Yesterday I meet my friend."
        val res = correctionEngine.analyzeUtterance(utterance)
        assertTrue("Error should be detected in: ", res.hasError)
        val corrected = res.correctedSentence ?: ""
        assertTrue("Expected 'met' in ''", corrected.contains("met", ignoreCase = true))
    }

    // 5. "She don't like coffee." (Subject-verb agreement don't -> doesn't)
    @Test
    fun test05_sheDontLike_incorrectCaught() {
        val utterance = "She don't like coffee."
        val res = correctionEngine.analyzeUtterance(utterance)
        assertTrue("Error should be detected in: ", res.hasError)
        val corrected = res.correctedSentence ?: ""
        assertTrue("Expected 'doesn't' in ''", corrected.contains("doesn't", ignoreCase = true))
    }

    // 6. "I discussed about this problem." (Discussed about -> discussed)
    @Test
    fun test06_discussedAbout_incorrectCaught() {
        val utterance = "I discussed about this problem."
        val res = correctionEngine.analyzeUtterance(utterance)
        assertTrue("Error should be detected in: ", res.hasError)
        val corrected = res.correctedSentence ?: ""
        assertTrue("Expected 'discussed this problem' in ''", corrected.contains("discussed this problem", ignoreCase = true))
    }

    // -------------------------------------------------------------
    // NEGATIVE TESTS: Correct Sentences MUST NOT be Falsely Corrected
    // -------------------------------------------------------------

    @Test
    fun test07_wentToJaipur_correctNotFlagged() {
        val utterance = "I went to Jaipur yesterday."
        val res = correctionEngine.analyzeUtterance(utterance)
        assertFalse("Correct sentence must not have errors: ", res.hasError)
    }

    @Test
    fun test08_livedHereForFiveYears_correctNotFlagged() {
        val utterance = "I have lived here for five years."
        val res = correctionEngine.analyzeUtterance(utterance)
        assertFalse("Correct sentence must not have errors: ", res.hasError)
    }

    @Test
    fun test09_yesterdayMetFriend_correctNotFlagged() {
        val utterance = "Yesterday I met my friend."
        val res = correctionEngine.analyzeUtterance(utterance)
        assertFalse("Correct sentence must not have errors: ", res.hasError)
    }

    @Test
    fun test10_sheDoesntLikeCoffee_correctNotFlagged() {
        val utterance = "She doesn't like coffee."
        val res = correctionEngine.analyzeUtterance(utterance)
        assertFalse("Correct sentence must not have errors: ", res.hasError)
    }

    @Test
    fun test11_discussedThePlan_correctNotFlagged() {
        val utterance = "We discussed the plan thoroughly."
        val res = correctionEngine.analyzeUtterance(utterance)
        assertFalse("Correct sentence must not have errors: ", res.hasError)
    }

    @Test
    fun test12_arrivedInTheMorning_correctNotFlagged() {
        val utterance = "I arrived in the morning."
        val res = correctionEngine.analyzeUtterance(utterance)
        assertFalse("Correct sentence must not have errors: ", res.hasError)
    }
}
