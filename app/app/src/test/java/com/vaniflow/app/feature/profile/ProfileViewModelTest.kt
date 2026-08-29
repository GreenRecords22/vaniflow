package com.vaniflow.app.feature.profile

import com.vaniflow.app.domain.model.ModelMetadata
import com.vaniflow.app.domain.model.ModelState
import com.vaniflow.app.domain.model.ModelType
import com.vaniflow.app.domain.repository.ProgressRepository
import com.vaniflow.app.domain.repository.SessionRepository
import com.vaniflow.app.domain.repository.VocabularyRepository
import com.vaniflow.app.engine.ai.cache.AIResponseCache
import com.vaniflow.app.engine.model.ModelManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {

    private val sessionRepository = mockk<SessionRepository>(relaxed = true)
    private val progressRepository = mockk<ProgressRepository>(relaxed = true)
    private val vocabularyRepository = mockk<VocabularyRepository>(relaxed = true)
    private val aiResponseCache = mockk<AIResponseCache>(relaxed = true)
    private val modelManager = mockk<ModelManager>(relaxed = true)
    private val dispatcher = StandardTestDispatcher()

    private val largeModel = ModelMetadata(
        id = "llm_big",
        name = "Big LLM",
        type = ModelType.LLM,
        version = "1.0.0",
        sizeBytes = 1_100_000_000L,
        sha256Checksum = "x",
        recommendedTier = com.vaniflow.app.domain.model.DeviceTier.MEDIUM,
        downloadUrl = "https://example.com/big",
        localFileName = "big.onnx",
        description = "Big"
    )

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        every { modelManager.availableModels } returns listOf(largeModel)
        every { modelManager.downloadStates } returns MutableStateFlow(emptyMap())
        every { modelManager.getModelState(any()) } returns ModelState.NOT_INSTALLED
        every { modelManager.getModelMetadata("llm_big") } returns largeModel
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `download with insufficient storage shows friendly error and does not start download`() = runTest {
        every { modelManager.hasSufficientStorage(any()) } returns false
        val vm = ProfileViewModel(sessionRepository, progressRepository, vocabularyRepository, aiResponseCache, modelManager)
        dispatcher.scheduler.advanceUntilIdle()

        vm.downloadModel("llm_big")
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(
            "Not enough storage to install Big LLM.",
            vm.uiState.value.modelError
        )
        coVerify(exactly = 0) { modelManager.downloadModel("llm_big") }
    }

    @Test
    fun `download with sufficient storage starts download and clears error`() = runTest {
        every { modelManager.hasSufficientStorage(any()) } returns true
        val downloadFlow = kotlinx.coroutines.flow.flow {
            emit(com.vaniflow.app.domain.model.ModelDownloadProgress("llm_big", 0, 1_100_000_000L, 0f, ModelState.DOWNLOADING))
        }
        coEvery { modelManager.downloadModel(any()) } returns downloadFlow
        val vm = ProfileViewModel(sessionRepository, progressRepository, vocabularyRepository, aiResponseCache, modelManager)
        dispatcher.scheduler.advanceUntilIdle()

        vm.downloadModel("llm_big")
        dispatcher.scheduler.advanceUntilIdle()

        assertNull(vm.uiState.value.modelError)
        coVerify(exactly = 1) { modelManager.downloadModel("llm_big") }
    }

    @Test
    fun `download of unknown model shows unavailable error`() = runTest {
        every { modelManager.getModelMetadata("nope") } returns null
        val vm = ProfileViewModel(sessionRepository, progressRepository, vocabularyRepository, aiResponseCache, modelManager)
        dispatcher.scheduler.advanceUntilIdle()

        vm.downloadModel("nope")
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("This model is unavailable.", vm.uiState.value.modelError)
        coVerify(exactly = 0) { modelManager.downloadModel("nope") }
    }
}
