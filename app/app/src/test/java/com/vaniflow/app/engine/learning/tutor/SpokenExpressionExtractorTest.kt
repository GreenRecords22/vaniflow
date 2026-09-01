package com.vaniflow.app.engine.learning.tutor

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class SpokenExpressionExtractorTest {

    private lateinit var extractor: SpokenExpressionExtractor

    @Before
    fun setup() {
        extractor = SpokenExpressionExtractor()
    }

    @Test
    fun `extractExpressions extracts looking forward to`() {
        val text = "I am looking forward to our next meeting tomorrow."
        val results = extractor.extractExpressions(text)

        assertEquals(1, results.size)
        assertEquals("looking forward to", results.first().wordOrPhrase)
        assertEquals("idiom/expression", results.first().partOfSpeech)
    }

    @Test
    fun `extractExpressions extracts by the way and in my opinion`() {
        val text = "By the way, in my opinion, practice makes everything better."
        val results = extractor.extractExpressions(text)

        assertEquals(2, results.size)
        assertTrue(results.any { it.wordOrPhrase == "by the way" })
        assertTrue(results.any { it.wordOrPhrase == "in my opinion" })
    }

    @Test
    fun `extractExpressions ignores standard text without expressions`() {
        val text = "I like apples and bananas."
        val results = extractor.extractExpressions(text)

        assertTrue(results.isEmpty())
    }
}
