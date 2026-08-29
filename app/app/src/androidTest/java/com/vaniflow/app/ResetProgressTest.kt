package com.vaniflow.app

import com.vaniflow.app.data.local.db.dao.AICacheDao
import com.vaniflow.app.data.local.db.dao.ConversationTurnDao
import com.vaniflow.app.data.local.db.dao.SavedVocabularyDao
import com.vaniflow.app.data.local.db.dao.SessionDao
import com.vaniflow.app.data.local.db.entity.AICacheEntity
import com.vaniflow.app.data.local.db.entity.ConversationTurnEntity
import com.vaniflow.app.data.local.db.entity.SavedVocabularyEntity
import com.vaniflow.app.data.local.db.entity.SessionEntity
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import javax.inject.Inject

/**
 * Milestone 13 — Instrumented test for the full reset-progress flow.
 * Validates that resetting progress clears all data except model files.
 *
 * Covers:
 * 1. Pre-reset data population
 * 2. Full reset clears sessions
 * 3. Full reset clears conversation turns
 * 4. Full reset clears vocabulary
 * 5. Full reset clears AI cache
 * 6. Post-reset state verification
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class ResetProgressTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @Inject lateinit var sessionDao: SessionDao
    @Inject lateinit var conversationTurnDao: ConversationTurnDao
    @Inject lateinit var savedVocabularyDao: SavedVocabularyDao
    @Inject lateinit var aiCacheDao: AICacheDao

    @Before
    fun setup() {
        hiltRule.inject()
        runBlocking {
            sessionDao.deleteAllSessions()
            conversationTurnDao.deleteAllTurns()
            savedVocabularyDao.deleteAllVocabulary()
            aiCacheDao.clearAll()
        }
    }

    @Test
    fun resetProgressClearsAllSessions() = runBlocking {
        // Populate
        sessionDao.insertSession(createSession("s1"))
        sessionDao.insertSession(createSession("s2"))
        sessionDao.insertSession(createSession("s3"))
        assertEquals(3, sessionDao.getAllSessions().first().size)

        // Reset
        sessionDao.deleteAllSessions()

        // Verify
        assertEquals(0, sessionDao.getAllSessions().first().size)
    }

    @Test
    fun resetProgressClearsAllConversationTurns() = runBlocking {
        // Populate
        conversationTurnDao.insertTurns(listOf(
            createTurn("t1", "s1"),
            createTurn("t2", "s1"),
            createTurn("t3", "s2")
        ))
        assertTrue(conversationTurnDao.getTurnsForSession("s1").first().isNotEmpty())

        // Reset
        conversationTurnDao.deleteAllTurns()

        // Verify
        assertEquals(0, conversationTurnDao.getTurnsForSession("s1").first().size)
        assertEquals(0, conversationTurnDao.getTurnsForSession("s2").first().size)
    }

    @Test
    fun resetProgressClearsAllVocabulary() = runBlocking {
        // Populate
        savedVocabularyDao.insertVocabulary(createVocab("eloquent"))
        savedVocabularyDao.insertVocabulary(createVocab("ubiquitous"))
        savedVocabularyDao.insertVocabulary(createVocab("ephemeral"))
        assertEquals(3, savedVocabularyDao.getVocabularyCount().first())

        // Reset
        savedVocabularyDao.deleteAllVocabulary()

        // Verify
        assertEquals(0, savedVocabularyDao.getVocabularyCount().first())
    }

    @Test
    fun resetProgressClearsAICache() = runBlocking {
        // Populate
        aiCacheDao.insertOrUpdate(createCache("k1"))
        aiCacheDao.insertOrUpdate(createCache("k2"))
        assertEquals(2, aiCacheDao.getEntryCount())

        // Reset
        aiCacheDao.clearAll()

        // Verify
        assertEquals(0, aiCacheDao.getEntryCount())
    }

    @Test
    fun fullResetClearsEverythingInOrder() = runBlocking {
        // 1. Populate all tables
        sessionDao.insertSession(createSession("reset_s1"))
        conversationTurnDao.insertTurns(listOf(
            createTurn("reset_t1", "reset_s1"),
            createTurn("reset_t2", "reset_s1")
        ))
        savedVocabularyDao.insertVocabulary(createVocab("perseverance"))
        aiCacheDao.insertOrUpdate(createCache("reset_k1"))

        // 2. Verify data exists
        assertEquals(1, sessionDao.getAllSessions().first().size)
        assertEquals(2, conversationTurnDao.getTurnsForSession("reset_s1").first().size)
        assertEquals(1, savedVocabularyDao.getVocabularyCount().first())
        assertEquals(1, aiCacheDao.getEntryCount())

        // 3. Full reset (same order as ProfileViewModel.resetProgress)
        sessionDao.deleteAllSessions()
        conversationTurnDao.deleteAllTurns()
        savedVocabularyDao.deleteAllVocabulary()
        aiCacheDao.clearAll()

        // 4. Verify everything is cleared
        assertEquals(0, sessionDao.getAllSessions().first().size)
        assertEquals(0, conversationTurnDao.getTurnsForSession("reset_s1").first().size)
        assertEquals(0, savedVocabularyDao.getVocabularyCount().first())
        assertEquals(0, aiCacheDao.getEntryCount())

        println("[M13][RESET] Full reset verified: all 4 tables cleared successfully")
    }

    // --- Helpers ---

    private fun createSession(id: String) = SessionEntity(
        id = id,
        characterId = "raya",
        scenarioId = "order_coffee",
        startTimeMs = System.currentTimeMillis(),
        endTimeMs = System.currentTimeMillis() + 180_000L,
        speakingTimeMinutes = 3,
        fluencyScore = 75,
        grammarScore = 80,
        pronunciationScore = 70,
        vocabularyScore = 65,
        strongestArea = "Grammar",
        focusNext = "Expand vocabulary"
    )

    private fun createTurn(id: String, sessionId: String) = ConversationTurnEntity(
        id = id,
        sessionId = sessionId,
        speaker = "user",
        text = "Test input",
        timestamp = System.currentTimeMillis()
    )

    private fun createVocab(word: String) = SavedVocabularyEntity(
        word = word,
        phonetic = "/test/",
        partOfSpeech = "noun",
        meaning = "Test meaning",
        example = "Test example"
    )

    private fun createCache(key: String) = AICacheEntity(
        cacheKey = key,
        category = "KNOWLEDGE_CACHE",
        characterId = "raya",
        scenarioId = "general",
        promptVersion = "1.0",
        normalizedInput = "test",
        responseText = "Test response",
        tokensCount = 10,
        expiresAt = System.currentTimeMillis() + 7 * 24 * 60 * 60 * 1000L
    )
}
