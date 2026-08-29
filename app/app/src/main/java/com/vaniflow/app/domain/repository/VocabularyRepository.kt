package com.vaniflow.app.domain.repository

import com.vaniflow.app.data.local.db.entity.SavedVocabularyEntity
import kotlinx.coroutines.flow.Flow

interface VocabularyRepository {
    fun getAllSavedVocabulary(): Flow<List<SavedVocabularyEntity>>
    fun getSavedVocabularyCount(): Flow<Int>
    suspend fun saveWord(vocab: SavedVocabularyEntity)
    suspend fun deleteWord(word: String)
    suspend fun clearAllVocabulary()
}
