package com.vaniflow.app.engine.learning.tutor

import com.vaniflow.app.data.repository.DefaultVocabularyMemoryRepository
import com.vaniflow.app.engine.learning.tutor.model.VocabularyMemory
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VocabularyMemoryTest {

    private lateinit var memoryManager: LearningMemoryManager
    private lateinit var repo: DefaultVocabularyMemoryRepository

    @Before
    fun setup() {
        repo = mockk(relaxed = true)
        memoryManager = LearningMemoryManager()
    }

    @Test
    fun `rememberVocabulary stores useful expression`() = runTest {
        val memory = VocabularyMemory(
            wordOrPhrase = "looking forward to",
            meaning = "awaiting with anticipation",
            exampleSentence = "I am looking forward to our meeting.",
            sourceScenarioId = "work_meeting"
        )

        assertEquals("looking forward to", memory.wordOrPhrase)
        assertEquals(20, memory.familiarityScore)
        assertEquals(1, memory.usageCount)
    }
}
