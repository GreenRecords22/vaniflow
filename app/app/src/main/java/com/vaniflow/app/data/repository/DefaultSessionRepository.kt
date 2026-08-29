package com.vaniflow.app.data.repository

import com.vaniflow.app.data.local.db.dao.ConversationTurnDao
import com.vaniflow.app.data.local.db.dao.SessionDao
import com.vaniflow.app.data.local.db.entity.ConversationTurnEntity
import com.vaniflow.app.data.local.db.entity.SessionEntity
import com.vaniflow.app.domain.repository.SessionRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultSessionRepository @Inject constructor(
    private val sessionDao: SessionDao,
    private val conversationTurnDao: ConversationTurnDao
) : SessionRepository {

    override fun getAllSessions(): Flow<List<SessionEntity>> {
        return sessionDao.getAllSessions()
    }

    override fun getSessionById(sessionId: String): Flow<SessionEntity?> {
        return sessionDao.getSessionFlowById(sessionId)
    }

    override suspend fun getSession(sessionId: String): SessionEntity? {
        return sessionDao.getSessionById(sessionId)
    }

    override suspend fun saveSession(session: SessionEntity) {
        sessionDao.insertSession(session)
    }

    override fun getTurnsForSession(sessionId: String): Flow<List<ConversationTurnEntity>> {
        return conversationTurnDao.getTurnsForSession(sessionId)
    }

    override suspend fun saveTurns(turns: List<ConversationTurnEntity>) {
        conversationTurnDao.insertTurns(turns)
    }

    override suspend fun clearAllSessions() {
        sessionDao.deleteAllSessions()
        conversationTurnDao.deleteAllTurns()
    }
}
