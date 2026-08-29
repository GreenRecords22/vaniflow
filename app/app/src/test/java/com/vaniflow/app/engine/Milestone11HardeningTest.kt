package com.vaniflow.app.engine

import android.content.Context
import com.vaniflow.app.data.local.db.dao.AICacheDao
import com.vaniflow.app.data.local.db.dao.ConversationTurnDao
import com.vaniflow.app.data.local.db.dao.SavedVocabularyDao
import com.vaniflow.app.data.local.db.dao.SessionDao
import com.vaniflow.app.data.local.db.entity.AICacheEntity
import com.vaniflow.app.data.repository.DefaultProgressRepository
import com.vaniflow.app.data.repository.DefaultSessionRepository
import com.vaniflow.app.data.repository.DefaultVocabularyRepository
import com.vaniflow.app.domain.model.ModelState
import com.vaniflow.app.engine.ai.AIRoutingLevel
import com.vaniflow.app.engine.ai.AIResult
import com.vaniflow.app.engine.ai.DefaultCloudAIProvider
import com.vaniflow.app.engine.ai.FallbackAIEngine
import com.vaniflow.app.engine.ai.LocalAIEngine
import com.vaniflow.app.engine.ai.SmartAIRouter
import com.vaniflow.app.engine.ai.cache.AIResponseCache
import com.vaniflow.app.engine.ai.cache.CacheCategory
import com.vaniflow.app.engine.ai.cache.DefaultAIResponseCache
import com.vaniflow.app.engine.model.DefaultModelManager
import com.vaniflow.app.feature.profile.ProfileViewModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class Milestone11HardeningTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var mockContext: Context
    private lateinit var tempDir: File

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        tempDir = File(System.getProperty("java.io.tmpdir"), "vaniflow_m11_test_${System.currentTimeMillis()}").apply { mkdirs() }
        mockContext = mockk(relaxed = true)
        every { mockContext.filesDir } returns tempDir
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testOfflineRouterWorksWithoutNetwork() = runTest {
        val fallback = FallbackAIEngine()
        val local = LocalAIEngine(mockk(relaxed = true))
        val cloud = DefaultCloudAIProvider()
        val cache = mockk<AIResponseCache>(relaxed = true)
        coEvery { cache.getCachedResponse(any(), any(), any(), any(), any(), any()) } returns null

        val router = SmartAIRouter(
            localAIEngine = local,
            cloudAIProvider = cloud,
            fallbackAIEngine = fallback,
            aiResponseCache = cache
        )

        // Verify offline routing generates valid fallback response without cloud
        val result = router.generateResponse(
            systemPrompt = "You are Raya",
            conversationHistory = emptyList(),
            userInput = "I would like a cappuccino"
        )
        assertTrue(result is AIResult.Success)
        val success = result as AIResult.Success
        assertTrue(success.text.isNotBlank())
    }

    @Test
    fun testCacheDatabaseExceptionGracefullyFallsBackToMiss() = runTest {
        val mockDao = mockk<AICacheDao>()
        coEvery { mockDao.getByKey(any()) } throws RuntimeException("Corrupted SQLite Cache Table")

        val cache = DefaultAIResponseCache(mockDao)
        val result = cache.getCachedResponse(
            category = CacheCategory.KNOWLEDGE_CACHE,
            characterId = "raya",
            scenarioId = "coffee",
            userInput = "hello",
            contextHash = "hash123",
            promptVersion = "1.0"
        )

        // Should return null (cache miss) instead of throwing exception
        assertNull(result)
    }

    @Test
    fun testDoNotCacheCategoryNeverTouchesDatabase() = runTest {
        val mockDao = mockk<AICacheDao>(relaxed = true)
        val cache = DefaultAIResponseCache(mockDao)

        val result = cache.getCachedResponse(
            category = CacheCategory.DO_NOT_CACHE,
            characterId = "raya",
            scenarioId = "coffee",
            userInput = "my password is 123",
            contextHash = "hash123",
            promptVersion = "1.0"
        )

        assertNull(result)
        coVerify(exactly = 0) { mockDao.getByKey(any()) }
    }

    @Test
    fun testCacheHitIncrementsEstimatedTokenSavings() = runTest {
        val mockDao = mockk<AICacheDao>(relaxed = true)
        val entity = AICacheEntity(
            cacheKey = "test_key",
            category = CacheCategory.KNOWLEDGE_CACHE.name,
            characterId = "raya",
            scenarioId = "coffee",
            promptVersion = "1.0",
            normalizedInput = "hello",
            responseText = "Hello there! How can I help you today?",
            tokensCount = 10,
            createdAt = System.currentTimeMillis(),
            lastAccessedAt = System.currentTimeMillis(),
            expiresAt = System.currentTimeMillis() + 100_000L
        )

        coEvery { mockDao.getByKey(any()) } returns entity
        val cache = DefaultAIResponseCache(mockDao)

        val result = cache.getCachedResponse(
            category = CacheCategory.KNOWLEDGE_CACHE,
            characterId = "raya",
            scenarioId = "coffee",
            userInput = "hello",
            contextHash = "hash",
            promptVersion = "1.0"
        )

        assertNotNull(result)
        val stats = cache.usageStats.value
        assertEquals(1, stats.cacheHits)
        assertEquals(10L, stats.estimatedTokensSaved)
        assertEquals(1, stats.cloudRequestsAvoided)
    }

    @Test
    fun testResetProgressClearsDataWhilePreservingOfflineModels() = runTest {
        val mockSessionDao = mockk<SessionDao>(relaxed = true)
        val mockTurnDao = mockk<ConversationTurnDao>(relaxed = true)
        val mockVocabDao = mockk<SavedVocabularyDao>(relaxed = true)
        val mockCacheDao = mockk<AICacheDao>(relaxed = true)

        val sessionRepo = DefaultSessionRepository(mockSessionDao, mockTurnDao)
        val progressRepo = DefaultProgressRepository(mockSessionDao)
        val vocabRepo = DefaultVocabularyRepository(mockVocabDao)
        val cache = DefaultAIResponseCache(mockCacheDao)

        // Setup a model file in disk
        val modelsDir = File(tempDir, "models").apply { mkdirs() }
        val modelFile = File(modelsDir, "stt_zipformer_small.onnx").apply { writeBytes(ByteArray(1024)) }
        val modelManager = DefaultModelManager(mockContext)
        val profileViewModel = ProfileViewModel(
            sessionRepository = sessionRepo,
            progressRepository = progressRepo,
            vocabularyRepository = vocabRepo,
            aiResponseCache = cache,
            modelManager = modelManager
        )

        profileViewModel.resetProgress()
        testScheduler.advanceUntilIdle()

        // Verify databases cleared
        coVerify(exactly = 1) { mockSessionDao.deleteAllSessions() }
        coVerify(exactly = 1) { mockTurnDao.deleteAllTurns() }
        coVerify(exactly = 1) { mockVocabDao.deleteAllVocabulary() }
        coVerify(exactly = 1) { mockCacheDao.clearAll() }

        // Verify model file remains intact on disk
        assertTrue(modelFile.exists())
        assertEquals(ModelState.INSTALLED, modelManager.getModelState("stt_sherpa_zipformer_small"))
    }

    @Test
    fun testCorruptedModelChecksumTriggersQuarantineAndMarkFailed() = runTest {
        val modelsDir = File(tempDir, "models").apply { mkdirs() }
        val modelFile = File(modelsDir, "stt_zipformer_small.onnx").apply {
            // Write corrupted bytes
            writeBytes("Corrupted invalid data".toByteArray())
        }

        val modelManager = DefaultModelManager(mockContext)
        val isValid = modelManager.verifyModelChecksum("stt_sherpa_zipformer_small")

        assertFalse(isValid)
        // Corrupted file was deleted/quarantined
        assertFalse(modelFile.exists())
    }

    @Test
    fun testStorageSafetyCheck() {
        val modelManager = DefaultModelManager(mockContext)
        // Check normal size within available temp disk space
        val hasSpace = modelManager.hasSufficientStorage(10 * 1024 * 1024L)
        assertTrue(hasSpace)
    }
}
