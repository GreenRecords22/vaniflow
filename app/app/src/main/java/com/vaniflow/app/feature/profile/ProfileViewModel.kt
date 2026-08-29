package com.vaniflow.app.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaniflow.app.domain.model.ModelDownloadProgress
import com.vaniflow.app.domain.model.ModelMetadata
import com.vaniflow.app.domain.model.ModelState
import com.vaniflow.app.domain.repository.ProgressRepository
import com.vaniflow.app.domain.repository.SessionRepository
import com.vaniflow.app.domain.repository.VocabularyRepository
import com.vaniflow.app.engine.ai.cache.AIResponseCache
import com.vaniflow.app.engine.model.ModelManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ModelUiItem(
    val metadata: ModelMetadata,
    val state: ModelState,
    val progress: Float = 0f,
    val isDownloading: Boolean = false
)

data class ProfileUiState(
    val sessionCount: Int = 0,
    val totalSpeakingMinutes: Int = 0,
    val currentStreak: Int = 0,
    val savedWordsCount: Int = 0,
    val isResetDialogOpen: Boolean = false,
    val isResetCompleted: Boolean = false,
    val isModelManagementOpen: Boolean = false,
    val isPrivacyDialogOpen: Boolean = false,
    val isAboutDialogOpen: Boolean = false,
    val models: List<ModelUiItem> = emptyList(),
    val modelError: String? = null
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val progressRepository: ProgressRepository,
    private val vocabularyRepository: VocabularyRepository,
    private val aiResponseCache: AIResponseCache,
    private val modelManager: ModelManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        observeProfileStats()
        loadModelStates()
        observeDownloads()
    }

    private fun observeProfileStats() {
        viewModelScope.launch {
            combine(
                progressRepository.getProgress(),
                vocabularyRepository.getSavedVocabularyCount()
            ) { progress, vocabCount ->
                _uiState.update {
                    it.copy(
                        sessionCount = progress.sessionCount,
                        totalSpeakingMinutes = progress.totalMinutes,
                        currentStreak = progress.currentStreak,
                        savedWordsCount = vocabCount
                    )
                }
            }.collect {}
        }
    }

    private fun loadModelStates() {
        val items = modelManager.availableModels.map { meta ->
            val state = modelManager.getModelState(meta.id)
            ModelUiItem(
                metadata = meta,
                state = state,
                progress = if (state == ModelState.INSTALLED || state == ModelState.READY) 1.0f else 0.0f
            )
        }
        _uiState.update { it.copy(models = items) }
    }

    private fun observeDownloads() {
        viewModelScope.launch {
            modelManager.downloadStates.collect { statesMap ->
                val currentModels = _uiState.value.models.map { item ->
                    val progressInfo = statesMap[item.metadata.id]
                    if (progressInfo != null) {
                        item.copy(
                            state = progressInfo.state,
                            progress = progressInfo.progressPercent,
                            isDownloading = progressInfo.state == ModelState.DOWNLOADING || progressInfo.state == ModelState.VERIFYING
                        )
                    } else {
                        item.copy(
                            state = modelManager.getModelState(item.metadata.id),
                            isDownloading = false
                        )
                    }
                }
                _uiState.update { it.copy(models = currentModels) }
            }
        }
    }

    fun openModelManagement() {
        loadModelStates()
        _uiState.update { it.copy(isModelManagementOpen = true) }
    }

    fun dismissModelManagement() {
        _uiState.update { it.copy(isModelManagementOpen = false) }
    }

    fun downloadModel(modelId: String) {
        val meta = modelManager.getModelMetadata(modelId)
        if (meta == null) {
            _uiState.update { it.copy(modelError = "This model is unavailable.") }
            return
        }
        if (!modelManager.hasSufficientStorage(meta.sizeBytes)) {
            _uiState.update {
                it.copy(
                    modelError = "Not enough storage to install ${meta.brandedName}."
                )
            }
            return
        }
        _uiState.update { it.copy(modelError = null) }
        viewModelScope.launch {
            try {
                modelManager.downloadModel(modelId).collect { progress ->
                    if (progress.state == ModelState.FAILED || progress.state == ModelState.ERROR) {
                        _uiState.update {
                            it.copy(modelError = "${meta.brandedName} couldn't be installed. Please try again.")
                        }
                    } else if (progress.state == ModelState.CORRUPTED) {
                        _uiState.update {
                            it.copy(modelError = "${meta.brandedName} download was incomplete. Please download again.")
                        }
                    }
                    loadModelStates()
                }
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(modelError = "${meta.brandedName} couldn't be installed. Please try again.")
                }
                loadModelStates()
            }
        }
    }

    fun cancelDownload(modelId: String) {
        modelManager.cancelDownload(modelId)
        loadModelStates()
    }

    fun clearModelError() {
        _uiState.update { it.copy(modelError = null) }
    }

    fun deleteModel(modelId: String) {
        viewModelScope.launch {
            modelManager.deleteModel(modelId)
            loadModelStates()
        }
    }

    fun openPrivacyDialog() {
        _uiState.update { it.copy(isPrivacyDialogOpen = true) }
    }

    fun dismissPrivacyDialog() {
        _uiState.update { it.copy(isPrivacyDialogOpen = false) }
    }

    fun openAboutDialog() {
        _uiState.update { it.copy(isAboutDialogOpen = true) }
    }

    fun dismissAboutDialog() {
        _uiState.update { it.copy(isAboutDialogOpen = false) }
    }

    fun openResetDialog() {
        _uiState.update { it.copy(isResetDialogOpen = true, isResetCompleted = false) }
    }

    fun dismissResetDialog() {
        _uiState.update { it.copy(isResetDialogOpen = false) }
    }

    fun resetProgress() {
        viewModelScope.launch {
            sessionRepository.clearAllSessions()
            vocabularyRepository.clearAllVocabulary()
            aiResponseCache.clearCache()
            _uiState.update {
                it.copy(
                    isResetDialogOpen = false,
                    isResetCompleted = true,
                    sessionCount = 0,
                    totalSpeakingMinutes = 0,
                    currentStreak = 0,
                    savedWordsCount = 0
                )
            }
        }
    }
}
