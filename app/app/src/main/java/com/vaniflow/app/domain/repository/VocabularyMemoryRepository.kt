package com.vaniflow.app.domain.repository

import com.vaniflow.app.engine.learning.tutor.model.VocabularyMemory
import kotlinx.coroutines.flow.Flow

interface VocabularyMemoryRepository {
    fun getAllVocabularyMemoryFlow(): Flow<List<VocabularyMemory>>
    suspend fun getExpressionsNeedingPractice(): List<VocabularyMemory>
    suspend fun saveExpression(memory: VocabularyMemory)
    suspend fun deleteExpression(id: String)
    suspend fun clearAllVocabularyMemory()
}
