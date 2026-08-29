package com.vaniflow.app.domain.repository

import com.vaniflow.app.data.local.db.entity.ConversationTurnEntity
import com.vaniflow.app.data.local.db.entity.SessionEntity
import kotlinx.coroutines.flow.Flow

interface SessionRepository {
    fun getAllSessions(): Flow<List<SessionEntity>>
    fun getSessionById(sessionId: String): Flow<SessionEntity?>
    suspend fun getSession(sessionId: String): SessionEntity?
    suspend fun saveSession(session: SessionEntity)
    fun getTurnsForSession(sessionId: String): Flow<List<ConversationTurnEntity>>
    suspend fun saveTurns(turns: List<ConversationTurnEntity>)
    suspend fun clearAllSessions()
}
