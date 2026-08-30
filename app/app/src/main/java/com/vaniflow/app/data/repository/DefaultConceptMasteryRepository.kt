package com.vaniflow.app.data.repository

import com.vaniflow.app.data.local.db.dao.ConceptMasteryDao
import com.vaniflow.app.data.local.db.entity.ConceptMasteryEntity
import com.vaniflow.app.domain.repository.ConceptMasteryRepository
import com.vaniflow.app.engine.learning.tutor.EnglishErrorCategory
import com.vaniflow.app.engine.learning.tutor.model.MasteryState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultConceptMasteryRepository @Inject constructor(
    private val conceptMasteryDao: ConceptMasteryDao
) : ConceptMasteryRepository {

    override fun getAllMasteryFlow(): Flow<List<MasteryState>> {
        return conceptMasteryDao.getAllMasteryFlow().map { list ->
            list.map { it.toDomain() }
        }
    }

    override suspend fun getAllMastery(): List<MasteryState> {
        return conceptMasteryDao.getAllMastery().map { it.toDomain() }
    }

    override suspend fun getMastery(conceptId: String): MasteryState? {
        return conceptMasteryDao.getMastery(conceptId)?.toDomain()
    }

    override suspend fun saveMastery(mastery: MasteryState) {
        conceptMasteryDao.saveMastery(mastery.toEntity())
    }

    override suspend fun saveAllMastery(list: List<MasteryState>) {
        conceptMasteryDao.saveAllMastery(list.map { it.toEntity() })
    }

    override suspend fun clearAllMastery() {
        conceptMasteryDao.deleteAllMastery()
    }

    private fun ConceptMasteryEntity.toDomain(): MasteryState {
        return MasteryState(
            conceptId = conceptId,
            category = runCatching { EnglishErrorCategory.valueOf(category) }.getOrDefault(EnglishErrorCategory.NATURAL_PHRASING),
            masteryScore = masteryScore,
            attemptCount = attemptCount,
            successCount = successCount,
            failureCount = failureCount,
            consecutiveSuccesses = consecutiveSuccesses,
            consecutiveFailures = consecutiveFailures,
            lastPracticedEpochMs = lastPracticedEpochMs,
            lastSuccessEpochMs = lastSuccessEpochMs,
            practicePriority = practicePriority
        )
    }

    private fun MasteryState.toEntity(): ConceptMasteryEntity {
        return ConceptMasteryEntity(
            conceptId = conceptId,
            category = category.name,
            masteryScore = masteryScore,
            attemptCount = attemptCount,
            successCount = successCount,
            failureCount = failureCount,
            consecutiveSuccesses = consecutiveSuccesses,
            consecutiveFailures = consecutiveFailures,
            lastPracticedEpochMs = lastPracticedEpochMs,
            lastSuccessEpochMs = lastSuccessEpochMs,
            practicePriority = practicePriority
        )
    }
}
