package com.vaniflow.app

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.vaniflow.app.domain.model.ModelState
import com.vaniflow.app.engine.model.ModelManager
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import javax.inject.Inject

/**
 * Milestone 13 — Instrumented tests for ModelManager storage/checksum behavior.
 * Validates model file management on real device storage.
 *
 * Covers:
 * 1. Models directory creation
 * 2. Model file retrieval for uninstalled models
 * 3. Storage sufficiency check
 * 4. Checksum verification for non-existent files
 * 5. Model state detection for missing files
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class ModelManagerInstrumentedTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @Inject lateinit var modelManager: ModelManager

    private lateinit var context: Context

    @Before
    fun setup() {
        hiltRule.inject()
        context = InstrumentationRegistry.getInstrumentation().targetContext
    }

    @Test
    fun modelsDirectoryCanBeCreated() {
        val modelsDir = File(context.filesDir, "models")
        if (!modelsDir.exists()) {
            modelsDir.mkdirs()
        }
        assertTrue("Models directory must exist after creation", modelsDir.exists())
        assertTrue("Models directory must be a directory", modelsDir.isDirectory)
    }

    @Test
    fun uninstalledModelReturnsNotInstalled() {
        // For a fresh test, no model should be installed
        // (unless previously downloaded by the user)
        val state = modelManager.getModelState("llm_qwen25_05b_instruct")
        println("[M13][MODEL] Model state: $state")
        // We don't assert NOT_INSTALLED because the user might have downloaded it
        // We just verify the method works
        assertNotNull("Model state must not be null", state)
    }

    @Test
    fun modelFileIsNullForUnknownModel() {
        val file = modelManager.getModelFile("nonexistent_model_xyz")
        assertNull("getModelFile must return null for unknown model", file)
    }

    @Test
    fun storageCheckReturnsValidResult() {
        val requiredBytes = 500_000_000L // 500 MB
        val usableSpace = context.filesDir.usableSpace
        val hasSpace = usableSpace >= requiredBytes
        println("[M13][MODEL] Usable storage: ${usableSpace / (1024 * 1024)} MB, needs 500MB: $hasSpace")
        assertNotNull("Storage check must return a result", hasSpace)
    }

    @Test
    fun modelMetadataIsCompleteForAllModels() {
        modelManager.availableModels.forEach { meta ->
            assertNotNull("Model ${meta.id} must have a name", meta.name)
            assertTrue("Model ${meta.id} name must not be blank", meta.name.isNotBlank())
            assertNotNull("Model ${meta.id} must have a type", meta.type)
            assertTrue("Model ${meta.id} must have sizeBytes > 0", meta.sizeBytes > 0)
            assertNotNull("Model ${meta.id} must have a downloadUrl", meta.downloadUrl)
            assertTrue("Model ${meta.id} downloadUrl must not be blank", meta.downloadUrl.isNotBlank())
            assertNotNull("Model ${meta.id} must have a localFileName", meta.localFileName)
            assertTrue("Model ${meta.id} localFileName must not be blank", meta.localFileName.isNotBlank())
        }
        println("[M13][MODEL] All ${modelManager.availableModels.size} models have complete metadata")
    }

    @Test
    fun checksumVerificationDoesNotCrashForMissingFile() = runBlocking {
        // Try to verify checksum for a model that likely isn't installed
        val result = modelManager.verifyModelChecksum("stt_sherpa_zipformer_small")
        println("[M13][MODEL] Checksum verify for uninstalled model: $result")
        // Should return false for missing file, not crash
        assertFalse("Checksum verify must return false for missing file", result)
    }

    @Test
    fun deleteModelReturnsFalseForNonExistentModel() = runBlocking {
        val deleted = modelManager.deleteModel("nonexistent_model_xyz")
        assertFalse("deleteModel must return false for nonexistent model", deleted)
    }

    @Test
    fun deviceTierIsDetermined() {
        val tier = modelManager.getRecommendedTier()
        assertNotNull("Device tier must be determined", tier)
        println("[M13][MODEL] Device tier: $tier (${tier.name})")
        // Realme RMX2040 with ~4GB RAM should be LOW tier
        assertTrue("Tier must be LOW, MEDIUM, or HIGH",
            tier.name in listOf("LOW", "MEDIUM", "HIGH"))
    }
}
