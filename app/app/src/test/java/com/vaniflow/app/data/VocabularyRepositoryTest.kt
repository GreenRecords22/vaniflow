package com.vaniflow.app.data

import com.vaniflow.app.data.local.db.dao.SavedVocabularyDao
import com.vaniflow.app.data.local.db.entity.SavedVocabularyEntity
import com.vaniflow.app.data.repository.DefaultVocabularyRepository
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class VocabularyRepositoryTest {

    private lateinit var mockDao: SavedVocabularyDao
    private lateinit var repository: DefaultVocabularyRepository

    @Before
    fun setup() {
        mockDao = mockk(relaxed = true)
        repository = DefaultVocabularyRepository(mockDao)
    }

    @Test
    fun testSaveWordInsertsToDao() = runTest {
        val word = SavedVocabularyEntity(
            word = "Confident",
            phonetic = "/ˈkɒnfɪdənt/",
            partOfSpeech = "Adjective",
            meaning = "Feeling certain",
            example = "She felt confident."
        )

        repository.saveWord(word)

        coVerify(exactly = 1) { mockDao.insertVocabulary(word) }
    }

    @Test
    fun testGetVocabularyCountFlow() = runTest {
        every { mockDao.getVocabularyCount() } returns flowOf(5)

        val count = repository.getSavedVocabularyCount().first()

        assertEquals(5, count)
    }

    @Test
    fun testClearAllVocabulary() = runTest {
        repository.clearAllVocabulary()

        coVerify(exactly = 1) { mockDao.deleteAllVocabulary() }
    }
}
