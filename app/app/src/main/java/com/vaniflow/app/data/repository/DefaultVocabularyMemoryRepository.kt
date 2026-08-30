package com.vaniflow.app.data.repository

import com.vaniflow.app.data.local.db.dao.VocabularyMemoryDao
import com.vaniflow.app.data.local.db.entity.VocabularyMemoryEntity
import com.vaniflow.app.domain.repository.VocabularyMemoryRepository
import com.vaniflow.app.engine.learning.tutor.model.VocabularyMemory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultVocabularyMemoryRepository @Inject constructor(
    private val vocabularyMemoryDao: VocabularyMemoryDao
) : VocabularyMemoryRepository {

    override fun getAllVocabularyMemoryFlow(): Flow<List<VocabularyMemory>> {
        return vocabularyMemoryDao.getAllVocabularyMemoryFlow().map { list ->
            list.map { it.toDomain() }
        }
    }

    override suspend fun getExpressionsNeedingPractice(): List<VocabularyMemory> {
        return vocabularyMemoryDao.getExpressionsNeedingPractice().map { it.toDomain() }
    }

    override suspend fun saveExpression(memory: VocabularyMemory) {
        vocabularyMemoryDao.insertVocabularyMemory(memory.toEntity())
    }

    override suspend fun deleteExpression(id: String) {
        vocabularyMemoryDao.deleteVocabularyMemory(id)
    }

    override suspend fun clearAllVocabularyMemory() {
        vocabularyMemoryDao.deleteAllVocabularyMemory()
    }

    private fun VocabularyMemoryEntity.toDomain(): VocabularyMemory {
        return VocabularyMemory(
            id = id,
            wordOrPhrase = wordOrPhrase,
            phonetic = phonetic,
            partOfSpeech = partOfSpeech,
            meaning = meaning,
            exampleSentence = exampleSentence,
            familiarityScore = familiarityScore,
            usageCount = usageCount,
            lastUsedEpochMs = lastUsedEpochMs,
            sourceScenarioId = sourceScenarioId
        )
    }

    private fun VocabularyMemory.toEntity(): VocabularyMemoryEntity {
        return VocabularyMemoryEntity(
            id = id,
            wordOrPhrase = wordOrPhrase,
            phonetic = phonetic,
            partOfSpeech = partOfSpeech,
            meaning = meaning,
            exampleSentence = exampleSentence,
            familiarityScore = familiarityScore,
            usageCount = usageCount,
            lastUsedEpochMs = lastUsedEpochMs,
            sourceScenarioId = sourceScenarioId
        )
    }
}
