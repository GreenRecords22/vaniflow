package com.vaniflow.app.engine

import com.vaniflow.app.engine.learning.VocabularyEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VocabularyEngineTest {

    private val vocabularyEngine = VocabularyEngine()

    @Test
    fun testDictionaryContainsCuratedWords() {
        val word = vocabularyEngine.getWordDetails("spearhead")
        assertNotNull(word)
        assertEquals("Spearhead", word!!.word)
        assertEquals("Verb", word.partOfSpeech)
        assertEquals("C1", word.cefrLevel)
    }

    @Test
    fun testExtractVocabularyFromText() {
        val sentence = "I felt confident when ordering a warm cappuccino at the cafe."
        val extracted = vocabularyEngine.extractVocabulary(sentence)

        assertEquals(2, extracted.size)
        assertTrue(extracted.any { it.word == "Confident" })
        assertTrue(extracted.any { it.word == "Cappuccino" })
    }

    @Test
    fun testNonExistentWordReturnsNull() {
        val word = vocabularyEngine.getWordDetails("nonexistentxyz")
        assertNull(word)
    }
}
