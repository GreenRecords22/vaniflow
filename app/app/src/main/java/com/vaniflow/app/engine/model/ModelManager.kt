package com.vaniflow.app.engine.model

import com.vaniflow.app.domain.model.DeviceTier
import com.vaniflow.app.domain.model.ModelDownloadProgress
import com.vaniflow.app.domain.model.ModelMetadata
import com.vaniflow.app.domain.model.ModelState
import com.vaniflow.app.domain.model.ModelType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/**
 * Production ModelManager interface responsible for downloading,
 * verifying, updating, and deleting downloadable on-device AI models.
 */
interface ModelManager {
    val availableModels: List<ModelMetadata>
    val downloadStates: StateFlow<Map<String, ModelDownloadProgress>>

    fun getRecommendedTier(): DeviceTier
    fun getModelMetadata(modelId: String): ModelMetadata?
    fun getModelState(modelId: String): ModelState
    fun getModelFile(modelId: String): File?

    /** Returns true if the device has enough free storage (with safety margin) to download [requiredBytes]. */
    fun hasSufficientStorage(requiredBytes: Long): Boolean

    suspend fun downloadModel(modelId: String): Flow<ModelDownloadProgress>
    fun cancelDownload(modelId: String)
    suspend fun verifyModelChecksum(modelId: String): Boolean
    suspend fun deleteModel(modelId: String): Boolean
}
