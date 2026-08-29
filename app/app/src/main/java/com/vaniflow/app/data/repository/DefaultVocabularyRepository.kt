package com.vaniflow.app.data.repository

import com.vaniflow.app.data.local.db.dao.SavedVocabularyDao
import com.vaniflow.app.data.local.db.entity.SavedVocabularyEntity
import com.vaniflow.app.domain.repository.VocabularyRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultVocabularyRepository @Inject constructor(
    private val savedVocabularyDao: SavedVocabularyDao
) : VocabularyRepository {

    override fun getAllSavedVocabulary(): Flow<List<SavedVocabularyEntity>> {
        return savedVocabularyDao.getAllVocabulary()
    }

    override fun getSavedVocabularyCount(): Flow<Int> {
        return savedVocabularyDao.getVocabularyCount()
    }

    override suspend fun saveWord(vocab: SavedVocabularyEntity) {
        savedVocabularyDao.insertVocabulary(vocab)
    }

    override suspend fun deleteWord(word: String) {
        savedVocabularyDao.deleteVocabulary(word)
    }

    override suspend fun clearAllVocabulary() {
        savedVocabularyDao.deleteAllVocabulary()
    }
}
