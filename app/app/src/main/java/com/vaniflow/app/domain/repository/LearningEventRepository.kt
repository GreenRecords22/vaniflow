package com.vaniflow.app.domain.repository

import com.vaniflow.app.engine.learning.tutor.model.LearningEvent
import kotlinx.coroutines.flow.Flow

interface LearningEventRepository {
    fun getAllEvents(): Flow<List<LearningEvent>>
    suspend fun getEventsForSession(sessionId: String): List<LearningEvent>
    suspend fun getRecentEventsForConcept(conceptId: String): List<LearningEvent>
    suspend fun recordEvent(event: LearningEvent)
    suspend fun recordEvents(events: List<LearningEvent>)
    suspend fun clearAllEvents()
}
