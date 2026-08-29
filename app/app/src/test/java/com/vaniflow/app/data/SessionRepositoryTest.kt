package com.vaniflow.app.data

import com.vaniflow.app.data.local.db.dao.ConversationTurnDao
import com.vaniflow.app.data.local.db.dao.SessionDao
import com.vaniflow.app.data.local.db.entity.ConversationTurnEntity
import com.vaniflow.app.data.local.db.entity.SessionEntity
import com.vaniflow.app.data.repository.DefaultSessionRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

class SessionRepositoryTest {

    private lateinit var mockSessionDao: SessionDao
    private lateinit var mockTurnDao: ConversationTurnDao
    private lateinit var repository: DefaultSessionRepository

    @Before
    fun setup() {
        mockSessionDao = mockk(relaxed = true)
        mockTurnDao = mockk(relaxed = true)
        repository = DefaultSessionRepository(mockSessionDao, mockTurnDao)
    }

    @Test
    fun testSaveAndRetrieveSessionById() = runTest {
        val testSession = SessionEntity(
            id = "sess_100",
            characterId = "raya",
            scenarioId = "coffee",
            startTimeMs = 1000L,
            endTimeMs = 2000L,
            speakingTimeMinutes = 4,
            fluencyScore = 88,
            grammarScore = 82,
            pronunciationScore = 94,
            vocabularyScore = 79,
            strongestArea = "Pronunciation",
            focusNext = "Prepositions"
        )

        every { mockSessionDao.getSessionFlowById("sess_100") } returns flowOf(testSession)

        val retrieved = repository.getSessionById("sess_100").first()

        assertNotNull(retrieved)
        assertEquals("sess_100", retrieved!!.id)
        assertEquals(88, retrieved.fluencyScore)
    }

    @Test
    fun testSaveAndRetrieveTurns() = runTest {
        val turns = listOf(
            ConversationTurnEntity(
                id = "t1",
                sessionId = "sess_100",
                speaker = "AI",
                text = "Hello!",
                timestamp = 1000L
            ),
            ConversationTurnEntity(
                id = "t2",
                sessionId = "sess_100",
                speaker = "USER",
                text = "Good morning!",
                timestamp = 1500L
            )
        )

        every { mockTurnDao.getTurnsForSession("sess_100") } returns flowOf(turns)

        val retrievedTurns = repository.getTurnsForSession("sess_100").first()

        assertEquals(2, retrievedTurns.size)
        assertEquals("Hello!", retrievedTurns.first().text)
    }

    @Test
    fun testClearAllSessions() = runTest {
        repository.clearAllSessions()

        coVerify(exactly = 1) { mockSessionDao.deleteAllSessions() }
        coVerify(exactly = 1) { mockTurnDao.deleteAllTurns() }
    }
}
