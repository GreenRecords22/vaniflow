package com.vaniflow.app.engine

import com.vaniflow.app.engine.stt.IndianEnglishCorpus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IndianEnglishCorpusTest {

    @Test
    fun testCorpusHasDiverseUtterances() {
        val corpus = IndianEnglishCorpus.benchmarkSentences
        assertTrue(corpus.size >= 8)

        val domains = corpus.map { it.domain }.distinct()
        assertTrue(domains.contains("Daily Life"))
        assertTrue(domains.contains("Work / Standup"))
        assertTrue(domains.contains("Interview"))
        assertTrue(domains.contains("Travel"))
    }

    @Test
    fun testWERCalculationExactMatch() {
        val ref = "I have been living in Bangalore for four years"
        val hyp = "I have been living in Bangalore for four years"
        val wer = IndianEnglishCorpus.calculateWER(ref, hyp)
        assertEquals(0.0f, wer, 0.001f)
    }

    @Test
    fun testWERCalculationSubstitutionsAndDeletions() {
        val ref = "give me one hot cappuccino"
        val hyp = "give me hot coffee" // 1 deletion ('one'), 1 substitution ('cappuccino' -> 'coffee')
        val wer = IndianEnglishCorpus.calculateWER(ref, hyp)
        assertTrue("WER should reflect differences", wer > 0.0f && wer <= 0.6f)
    }

    @Test
    fun testWERCalculationEmptyStrings() {
        assertEquals(0.0f, IndianEnglishCorpus.calculateWER("", ""), 0.001f)
        assertEquals(1.0f, IndianEnglishCorpus.calculateWER("hello world", ""), 0.001f)
    }
}
