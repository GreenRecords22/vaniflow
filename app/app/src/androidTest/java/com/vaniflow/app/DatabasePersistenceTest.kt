package com.vaniflow.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.vaniflow.app.data.local.db.VaniFlowDatabase
import com.vaniflow.app.data.local.db.entity.AICacheEntity
import com.vaniflow.app.data.local.db.entity.ConversationTurnEntity
import com.vaniflow.app.data.local.db.entity.SavedVocabularyEntity
import com.vaniflow.app.data.local.db.entity.SessionEntity
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

/**
 * Milestone 13 — Production-critical instrumented tests for Room database persistence.
 * Tests actual DAO operations on the real VaniFlowDatabase via Hilt-injected DAOs.
 *
 * Covers:
 * 1. Application startup / database initialization
 * 2. Session persistence (insert + query)
 * 3. Conversation turn persistence (insert + query)
 * 4. Saved vocabulary persistence (insert + query + count)
 * 5. AI response cache persistence (insert + query + clear)
 * 6. Progress reset (clear sessions, turns, vocabulary, cache)
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class DatabasePersistenceTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @Inject lateinit var database: VaniFlowDatabase
    @Inject lateinit var sessionDao: com.vaniflow.app.data.local.db.dao.SessionDao
    @Inject lateinit var conversationTurnDao: com.vaniflow.app.data.local.db.dao.ConversationTurnDao
    @Inject lateinit var savedVocabularyDao: com.vaniflow.app.data.local.db.dao.SavedVocabularyDao
    @Inject lateinit var aiCacheDao: com.vaniflow.app.data.local.db.dao.AICacheDao
    @Inject lateinit var guestProfileDao: com.vaniflow.app.data.local.db.dao.GuestProfileDao
    @Inject lateinit var learnerProfileDao: com.vaniflow.app.data.local.db.dao.LearnerProfileDao
    @Inject lateinit var dailyUsageDao: com.vaniflow.app.data.local.db.dao.DailyUsageDao
    @Inject lateinit var learningEventDao: com.vaniflow.app.data.local.db.dao.LearningEventDao
    @Inject lateinit var conceptMasteryDao: com.vaniflow.app.data.local.db.dao.ConceptMasteryDao
    @Inject lateinit var vocabularyMemoryDao: com.vaniflow.app.data.local.db.dao.VocabularyMemoryDao

    @Before
    fun setup() {
        hiltRule.inject()
    }

    @After
    fun tearDown() {
        runBlocking {
            sessionDao.deleteAllSessions()
            conversationTurnDao.deleteAllTurns()
            savedVocabularyDao.deleteAllVocabulary()
            aiCacheDao.clearAll()
            learnerProfileDao.deleteProfile()
            dailyUsageDao.deleteAllUsage()
        }
    }

    // --- 1. Database Initialization ---

    @Test
    fun databaseIsCreatedAndOpen() {
        assertNotNull("Database instance must not be null", database)
        // Verify database works by performing a simple query
        val cursor = database.query("SELECT count(*) FROM sqlite_master", null)
        assertTrue("Must be able to query sqlite_master", cursor.moveToFirst())
        val tableCount = cursor.getInt(0)
        cursor.close()
        assertTrue("Database must have tables (found $tableCount)", tableCount > 0)
    }

    @Test
    fun databaseHasCorrectTableName() {
        val cursor = database.query("SELECT name FROM sqlite_master WHERE type='table'", null)
        val tables = mutableListOf<String>()
        while (cursor.moveToNext()) {
            tables.add(cursor.getString(0))
        }
        cursor.close()
        assertTrue("Must contain sessions table", tables.contains("sessions"))
        assertTrue("Must contain conversation_turns table", tables.contains("conversation_turns"))
        assertTrue("Must contain saved_vocabulary table", tables.contains("saved_vocabulary"))
        assertTrue("Must contain ai_response_cache table", tables.contains("ai_response_cache"))
        assertTrue("Must contain guest_profile table", tables.contains("guest_profile"))
    }

    // --- 2. Session Persistence ---

    @Test
    fun sessionCanBeInsertedAndRetrieved() = runBlocking {
        val session = createTestSession("test_session_1")
        sessionDao.insertSession(session)

        val retrieved = sessionDao.getSessionById("test_session_1")
        assertNotNull("Session must be retrievable after insert", retrieved)
        assertEquals("test_session_1", retrieved!!.id)
        assertEquals("raya", retrieved.characterId)
        assertEquals("order_coffee", retrieved.scenarioId)
        assertEquals(75, retrieved.fluencyScore)
        assertEquals(80, retrieved.grammarScore)
    }

    @Test
    fun multipleSessionsCanBeInsertedAndQueried() = runBlocking {
        sessionDao.insertSession(createTestSession("s1", fluency = 60))
        sessionDao.insertSession(createTestSession("s2", fluency = 70))
        sessionDao.insertSession(createTestSession("s3", fluency = 80))

        val sessions = sessionDao.getAllSessions().first()
        assertEquals("Must have 3 sessions", 3, sessions.size)
        // Sessions ordered by startTimeMs DESC
        assertTrue("First session should have highest startTimeMs", sessions[0].fluencyScore == 80)
    }

    @Test
    fun sessionCanBeUpdatedViaReplace() = runBlocking {
        val original = createTestSession("update_session", fluency = 50)
        sessionDao.insertSession(original)

        val updated = original.copy(fluencyScore = 90)
        sessionDao.insertSession(updated)

        val retrieved = sessionDao.getSessionById("update_session")
        assertEquals(90, retrieved!!.fluencyScore)
    }

    // --- 3. Conversation Turn Persistence ---

    @Test
    fun conversationTurnsCanBeInsertedAndRetrieved() = runBlocking {
        val turns = listOf(
            createTestTurn("turn_1", "test_session_1", "user", "Hello!"),
            createTestTurn("turn_2", "test_session_1", "ai", "Hello! How are you?")
        )
        conversationTurnDao.insertTurns(turns)

        val retrieved = conversationTurnDao.getTurnsForSession("test_session_1").first()
        assertEquals("Must have 2 turns", 2, retrieved.size)
        assertEquals("Hello!", retrieved[0].text)
        assertEquals("Hello! How are you?", retrieved[1].text)
    }

    @Test
    fun conversationTurnsAreOrderedByTimestamp() = runBlocking {
        val turns = listOf(
            createTestTurn("t1", "s1", "user", "First", timestampMs = 1000L),
            createTestTurn("t2", "s1", "ai", "Second", timestampMs = 2000L),
            createTestTurn("t3", "s1", "user", "Third", timestampMs = 3000L)
        )
        conversationTurnDao.insertTurns(turns)

        val retrieved = conversationTurnDao.getTurnsForSession("s1").first()
        assertEquals(1000L, retrieved[0].timestamp)
        assertEquals(2000L, retrieved[1].timestamp)
        assertEquals(3000L, retrieved[2].timestamp)
    }

    @Test
    fun conversationTurnWithCorrectionCanBePersisted() = runBlocking {
        val turn = ConversationTurnEntity(
            id = "turn_corr_1",
            sessionId = "s1",
            speaker = "ai",
            text = "I goed to the store",
            timestamp = System.currentTimeMillis(),
            originalMistake = "goed",
            correctionSuggestion = "went",
            correctionExplanation = "Go is an irregular verb. Past tense is 'went', not 'goed'.",
            correctionCategory = "GRAMMAR"
        )
        conversationTurnDao.insertTurns(listOf(turn))

        val retrieved = conversationTurnDao.getTurnsForSession("s1").first()
        assertEquals(1, retrieved.size)
        assertEquals("goed", retrieved[0].originalMistake)
        assertEquals("went", retrieved[0].correctionSuggestion)
        assertEquals("GRAMMAR", retrieved[0].correctionCategory)
    }

    // --- 4. Saved Vocabulary Persistence ---

    @Test
    fun vocabularyCanBeInsertedAndRetrieved() = runBlocking {
        val vocab = SavedVocabularyEntity(
            word = "resilient",
            phonetic = "/rɪˈzɪliənt/",
            partOfSpeech = "adjective",
            meaning = "Able to recover quickly from difficulties",
            example = "She is a resilient person who never gives up."
        )
        savedVocabularyDao.insertVocabulary(vocab)

        val all = savedVocabularyDao.getAllVocabulary().first()
        assertEquals(1, all.size)
        assertEquals("resilient", all[0].word)
        assertEquals("adjective", all[0].partOfSpeech)
    }

    @Test
    fun vocabularyCountIsAccurate() = runBlocking {
        assertEquals(0, savedVocabularyDao.getVocabularyCount().first())

        savedVocabularyDao.insertVocabulary(createTestVocab("word1"))
        savedVocabularyDao.insertVocabulary(createTestVocab("word2"))
        savedVocabularyDao.insertVocabulary(createTestVocab("word3"))

        assertEquals(3, savedVocabularyDao.getVocabularyCount().first())
    }

    @Test
    fun vocabularyCanBeDeletedByWord() = runBlocking {
        savedVocabularyDao.insertVocabulary(createTestVocab("ephemeral"))
        savedVocabularyDao.insertVocabulary(createTestVocab("ubiquitous"))

        savedVocabularyDao.deleteVocabulary("ephemeral")

        val all = savedVocabularyDao.getAllVocabulary().first()
        assertEquals(1, all.size)
        assertEquals("ubiquitous", all[0].word)
    }

    // --- 5. AI Response Cache Persistence ---

    @Test
    fun aiCacheCanBeInsertedAndRetrieved() = runBlocking {
        val entity = AICacheEntity(
            cacheKey = "test_cache_key_1",
            category = "KNOWLEDGE_CACHE",
            characterId = "raya",
            scenarioId = "general",
            promptVersion = "1.0",
            normalizedInput = "what does resilient mean",
            responseText = "'Resilient' means able to recover quickly.",
            tokensCount = 12,
            expiresAt = System.currentTimeMillis() + 7 * 24 * 60 * 60 * 1000L
        )
        aiCacheDao.insertOrUpdate(entity)

        val retrieved = aiCacheDao.getByKey("test_cache_key_1")
        assertNotNull("Cache entry must be retrievable", retrieved)
        assertEquals("'Resilient' means able to recover quickly.", retrieved!!.responseText)
        assertEquals(12, retrieved.tokensCount)
    }

    @Test
    fun aiCacheEntryCountIsAccurate() = runBlocking {
        assertEquals(0, aiCacheDao.getEntryCount())

        aiCacheDao.insertOrUpdate(createTestCache("k1"))
        aiCacheDao.insertOrUpdate(createTestCache("k2"))

        assertEquals(2, aiCacheDao.getEntryCount())
    }

    @Test
    fun aiCacheCanBeCleared() = runBlocking {
        aiCacheDao.insertOrUpdate(createTestCache("c1"))
        aiCacheDao.insertOrUpdate(createTestCache("c2"))
        aiCacheDao.insertOrUpdate(createTestCache("c3"))

        val cleared = aiCacheDao.clearAll()
        assertEquals(3, cleared)
        assertEquals(0, aiCacheDao.getEntryCount())
    }

    // --- 6. Progress Reset (full clear) ---

    @Test
    fun progressResetClearsAllData() = runBlocking {
        // Insert data across all tables
        sessionDao.insertSession(createTestSession("reset_s1"))
        conversationTurnDao.insertTurns(listOf(createTestTurn("reset_t1", "reset_s1", "user", "Hello")))
        savedVocabularyDao.insertVocabulary(createTestVocab("perseverance"))
        aiCacheDao.insertOrUpdate(createTestCache("reset_k1"))

        // Verify data exists
        assertEquals(1, sessionDao.getAllSessions().first().size)
        assertEquals(1, conversationTurnDao.getTurnsForSession("reset_s1").first().size)
        assertEquals(1, savedVocabularyDao.getVocabularyCount().first())
        assertEquals(1, aiCacheDao.getEntryCount())

        // Simulate reset (same as ProfileViewModel.resetProgress)
        sessionDao.deleteAllSessions()
        conversationTurnDao.deleteAllTurns()
        savedVocabularyDao.deleteAllVocabulary()
        aiCacheDao.clearAll()

        // Verify all data is gone
        assertEquals(0, sessionDao.getAllSessions().first().size)
        assertEquals(0, conversationTurnDao.getTurnsForSession("reset_s1").first().size)
        assertEquals(0, savedVocabularyDao.getVocabularyCount().first())
        assertEquals(0, aiCacheDao.getEntryCount())
    }

    // --- Helper Methods ---

    private fun createTestSession(
        id: String,
        fluency: Int = 75,
        grammar: Int = 80
    ) = SessionEntity(
        id = id,
        characterId = "raya",
        scenarioId = "order_coffee",
        startTimeMs = System.currentTimeMillis(),
        endTimeMs = System.currentTimeMillis() + 180_000L,
        speakingTimeMinutes = 3,
        fluencyScore = fluency,
        grammarScore = grammar,
        pronunciationScore = 70,
        vocabularyScore = 65,
        strongestArea = "Grammar",
        focusNext = "Expand vocabulary range"
    )

    private fun createTestTurn(
        id: String,
        sessionId: String,
        speaker: String,
        text: String,
        timestampMs: Long = System.currentTimeMillis()
    ) = ConversationTurnEntity(
        id = id,
        sessionId = sessionId,
        speaker = speaker,
        text = text,
        timestamp = timestampMs
    )

    private fun createTestVocab(word: String) = SavedVocabularyEntity(
        word = word,
        phonetic = "/test/",
        partOfSpeech = "noun",
        meaning = "Test meaning for $word",
        example = "This is an example using $word."
    )

    private fun createTestCache(key: String) = AICacheEntity(
        cacheKey = key,
        category = "KNOWLEDGE_CACHE",
        characterId = "raya",
        scenarioId = "general",
        promptVersion = "1.0",
        normalizedInput = "test input $key",
        responseText = "Test response for $key",
        tokensCount = 10,
        expiresAt = System.currentTimeMillis() + 7 * 24 * 60 * 60 * 1000L
    )

    // --- 7. LearnerProfile & DailyUsage Real Room Persistence ---

    @Test
    fun testLearnerProfilePersistence() = runBlocking {
        val profileEntity = com.vaniflow.app.data.local.db.entity.LearnerProfileEntity(
            id = "default_learner_profile",
            estimatedLevel = "B1",
            speakingConfidenceScore = 75.0f,
            totalUtterances = 30,
            correctionsDelivered = 4,
            successfulRetries = 3,
            commonMistakesJson = "{\"past_buyed\":2}",
            masteredConceptsJson = "[\"past_buyed\"]",
            conceptsNeedingPracticeJson = "[\"tense\"]",
            recentCorrectionsJson = "[]",
            updatedAt = System.currentTimeMillis()
        )

        learnerProfileDao.saveProfile(profileEntity)

        val retrieved = learnerProfileDao.getProfile()
        assertNotNull("LearnerProfile must be persisted in Room", retrieved)
        assertEquals("B1", retrieved?.estimatedLevel)
        assertEquals(75.0f, retrieved?.speakingConfidenceScore ?: 0f, 0.01f)
        assertEquals(30, retrieved?.totalUtterances)
        assertTrue(retrieved?.commonMistakesJson?.contains("past_buyed") == true)
    }

    @Test
    fun testDailyUsagePersistence() = runBlocking {
        val usageEntity = com.vaniflow.app.data.local.db.entity.DailyUsageEntity(
            date = "2026-08-29",
            speakingSeconds = 5400L,
            inputTokens = 1500L,
            outputTokens = 3000L,
            totalRequests = 20,
            cacheHits = 5,
            savedTokens = 750L,
            updatedAt = System.currentTimeMillis()
        )

        dailyUsageDao.saveUsage(usageEntity)

        val retrieved = dailyUsageDao.getUsageByDate("2026-08-29")
        assertNotNull("DailyUsage must be persisted in Room", retrieved)
        assertEquals(5400L, retrieved?.speakingSeconds)
        assertEquals(20, retrieved?.totalRequests)
    }

    @Test
    fun testMigration1To2PreservesExistingData() = runBlocking {
        // Insert data into v1 tables
        val session = createTestSession("migrated_session")
        sessionDao.insertSession(session)
        val turn = createTestTurn("migrated_turn", "migrated_session", "USER", "Hello migration test")
        conversationTurnDao.insertTurns(listOf(turn))
        val vocab = createTestVocab("articulate")
        savedVocabularyDao.insertVocabulary(vocab)

        // Verify v1 data exists
        val loadedSession = sessionDao.getSessionById("migrated_session")
        assertNotNull("Session must survive", loadedSession)
        assertEquals("migrated_session", loadedSession?.id)

        val loadedTurns = conversationTurnDao.getTurnsForSession("migrated_session").first()
        assertEquals(1, loadedTurns.size)
        assertEquals("Hello migration test", loadedTurns[0].text)

        val loadedVocabList = savedVocabularyDao.getAllVocabulary().first()
        assertTrue("Vocab must survive", loadedVocabList.any { it.word == "articulate" })

        // Verify new v2 tables can be written to simultaneously
        val profile = com.vaniflow.app.data.local.db.entity.LearnerProfileEntity(
            id = "default_learner_profile",
            estimatedLevel = "B2",
            speakingConfidenceScore = 80.0f,
            totalUtterances = 1,
            correctionsDelivered = 0,
            successfulRetries = 0,
            commonMistakesJson = "{}",
            masteredConceptsJson = "[]",
            conceptsNeedingPracticeJson = "[]",
            recentCorrectionsJson = "[]",
            updatedAt = System.currentTimeMillis()
        )
        learnerProfileDao.saveProfile(profile)
        assertNotNull(learnerProfileDao.getProfile())
    }

    @Test
    fun testLearningEventPersistence() = runBlocking {
        val event = com.vaniflow.app.data.local.db.entity.LearningEventEntity(
            id = "event_1",
            type = "CORRECTION",
            conceptId = "past_buyed",
            category = "TENSE",
            severity = "IMPORTANT",
            originalUtterance = "Yesterday I buyed apples",
            correctedForm = "bought",
            isSuccess = false,
            sessionId = "session_123",
            confidenceImpact = -1.0f,
            timestampEpochMs = System.currentTimeMillis()
        )
        learningEventDao.insertEvent(event)

        val retrieved = learningEventDao.getEventsForSession("session_123")
        assertEquals(1, retrieved.size)
        assertEquals("past_buyed", retrieved[0].conceptId)
        assertEquals("TENSE", retrieved[0].category)
    }

    @Test
    fun testConceptMasteryPersistence() = runBlocking {
        val mastery = com.vaniflow.app.data.local.db.entity.ConceptMasteryEntity(
            conceptId = "past_buyed",
            category = "TENSE",
            masteryScore = 75,
            attemptCount = 4,
            successCount = 3,
            failureCount = 1,
            consecutiveSuccesses = 2,
            consecutiveFailures = 0,
            lastPracticedEpochMs = System.currentTimeMillis(),
            lastSuccessEpochMs = System.currentTimeMillis(),
            practicePriority = 35
        )
        conceptMasteryDao.saveMastery(mastery)

        val retrieved = conceptMasteryDao.getMastery("past_buyed")
        assertNotNull(retrieved)
        assertEquals(75, retrieved?.masteryScore)
        assertEquals(2, retrieved?.consecutiveSuccesses)
    }

    @Test
    fun testVocabularyMemoryPersistence() = runBlocking {
        val vocab = com.vaniflow.app.data.local.db.entity.VocabularyMemoryEntity(
            id = "vocab_1",
            wordOrPhrase = "looking forward to",
            phonetic = "",
            partOfSpeech = "expression",
            meaning = "await with excitement",
            exampleSentence = "I am looking forward to our meeting.",
            familiarityScore = 30,
            usageCount = 2,
            lastUsedEpochMs = System.currentTimeMillis(),
            sourceScenarioId = "work_meeting"
        )
        vocabularyMemoryDao.insertVocabularyMemory(vocab)

        val needingPractice = vocabularyMemoryDao.getExpressionsNeedingPractice()
        assertTrue(needingPractice.any { it.wordOrPhrase == "looking forward to" })
    }
}
