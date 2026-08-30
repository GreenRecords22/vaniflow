package com.vaniflow.app.data.repository

import com.vaniflow.app.data.local.db.dao.LearningEventDao
import com.vaniflow.app.data.local.db.entity.LearningEventEntity
import com.vaniflow.app.domain.repository.LearningEventRepository
import com.vaniflow.app.engine.learning.tutor.CorrectionSeverity
import com.vaniflow.app.engine.learning.tutor.EnglishErrorCategory
import com.vaniflow.app.engine.learning.tutor.model.LearningEvent
import com.vaniflow.app.engine.learning.tutor.model.LearningEventType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultLearningEventRepository @Inject constructor(
    private val learningEventDao: LearningEventDao
) : LearningEventRepository {

    override fun getAllEvents(): Flow<List<LearningEvent>> {
        return learningEventDao.getAllEvents().map { list ->
            list.map { it.toDomain() }
        }
    }

    override suspend fun getEventsForSession(sessionId: String): List<LearningEvent> {
        return learningEventDao.getEventsForSession(sessionId).map { it.toDomain() }
    }

    override suspend fun getRecentEventsForConcept(conceptId: String): List<LearningEvent> {
        return learningEventDao.getRecentEventsForConcept(conceptId).map { it.toDomain() }
    }

    override suspend fun recordEvent(event: LearningEvent) {
        learningEventDao.insertEvent(event.toEntity())
    }

    override suspend fun recordEvents(events: List<LearningEvent>) {
        learningEventDao.insertEvents(events.map { it.toEntity() })
    }

    override suspend fun clearAllEvents() {
        learningEventDao.deleteAllEvents()
    }

    private fun LearningEventEntity.toDomain(): LearningEvent {
        return LearningEvent(
            id = id,
            type = runCatching { LearningEventType.valueOf(type) }.getOrDefault(LearningEventType.CORRECTION),
            conceptId = conceptId,
            category = runCatching { EnglishErrorCategory.valueOf(category) }.getOrDefault(EnglishErrorCategory.NATURAL_PHRASING),
            severity = runCatching { CorrectionSeverity.valueOf(severity) }.getOrDefault(CorrectionSeverity.MINOR),
            originalUtterance = originalUtterance,
            correctedForm = correctedForm,
            isSuccess = isSuccess,
            sessionId = sessionId,
            confidenceImpact = confidenceImpact,
            timestampEpochMs = timestampEpochMs
        )
    }

    private fun LearningEvent.toEntity(): LearningEventEntity {
        return LearningEventEntity(
            id = id,
            type = type.name,
            conceptId = conceptId,
            category = category.name,
            severity = severity.name,
            originalUtterance = originalUtterance,
            correctedForm = correctedForm,
            isSuccess = isSuccess,
            sessionId = sessionId,
            confidenceImpact = confidenceImpact,
            timestampEpochMs = timestampEpochMs
        )
    }
}
