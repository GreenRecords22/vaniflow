package com.vaniflow.app.engine

import com.vaniflow.app.engine.tts.SentenceSplitter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SentenceSplitterTest {

    @Test
    fun testEmptyAndBlankStrings() {
        assertTrue(SentenceSplitter.splitIntoSentences("").isEmpty())
        assertTrue(SentenceSplitter.splitIntoSentences("   ").isEmpty())
    }

    @Test
    fun testSingleSentence() {
        val input = "Hello! How are you doing today?"
        val sentences = SentenceSplitter.splitIntoSentences(input)
        assertEquals(2, sentences.size)
        assertEquals("Hello!", sentences[0])
        assertEquals("How are you doing today?", sentences[1])
    }

    @Test
    fun testMultiSentenceParagraph() {
        val input = "Good morning. Welcome to Artisanal Roasters! What can I get started for you today?"
        val sentences = SentenceSplitter.splitIntoSentences(input)
        assertEquals(3, sentences.size)
        assertEquals("Good morning.", sentences[0])
        assertEquals("Welcome to Artisanal Roasters!", sentences[1])
        assertEquals("What can I get started for you today?", sentences[2])
    }

    @Test
    fun testPreservesAbbreviations() {
        val input = "Dr. Sharma explained the project at 3.5 GHz vs. 2.4 GHz."
        val sentences = SentenceSplitter.splitIntoSentences(input)
        assertEquals(1, sentences.size)
        assertEquals("Dr. Sharma explained the project at 3.5 GHz vs. 2.4 GHz.", sentences[0])
    }
}
