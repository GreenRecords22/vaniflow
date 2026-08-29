package com.vaniflow.app.engine

import android.content.Context
import com.vaniflow.app.domain.model.ModelState
import com.vaniflow.app.domain.model.ModelType
import com.vaniflow.app.engine.model.DefaultModelManager
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ModelManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var mockContext: Context
    private lateinit var modelManager: DefaultModelManager

    @Before
    fun setup() {
        mockContext = mockk(relaxed = true)
        val filesDir = tempFolder.newFolder("files")
        every { mockContext.filesDir } returns filesDir

        modelManager = DefaultModelManager(mockContext)
    }

    @Test
    fun testCatalogContainsSTTModels() {
        val models = modelManager.availableModels
        assertTrue(models.isNotEmpty())

        val sttModel = models.find { it.type == ModelType.STT }
        assertNotNull(sttModel)
        assertTrue(sttModel!!.sizeBytes > 0)
        assertTrue(sttModel.localFileName.isNotBlank())
    }

    @Test
    fun testInitialModelStateIsNotInstalled() {
        val sttId = "stt_sherpa_zipformer_small"
        assertEquals(ModelState.NOT_INSTALLED, modelManager.getModelState(sttId))
    }

    /**
     * Verifies the manager's local file-state + checksum logic without performing a
     * real network download (the download path is exercised on-device, not in JVM
     * unit tests). Placeholder STT checksums are blank, so verification must pass
     * for any non-empty local file.
     */
    @Test
    fun testLocalModelInstalledStateAndVerification() = runTest {
        val sttId = "stt_sherpa_zipformer_small"
        val file = modelManager.getModelFile(sttId)
        assertNotNull(file)
        file!!.writeText("dummy offline unit-test model bytes")
        assertEquals(ModelState.INSTALLED, modelManager.getModelState(sttId))
        assertTrue(file.exists() && file.length() > 0)
    }

    @Test
    fun testDeleteModel() = runTest {
        val sttId = "stt_sherpa_zipformer_small"
        val file = modelManager.getModelFile(sttId)
        assertNotNull(file)
        file!!.writeText("dummy")
        assertTrue(modelManager.deleteModel(sttId))
        assertEquals(ModelState.NOT_INSTALLED, modelManager.getModelState(sttId))
    }
}
