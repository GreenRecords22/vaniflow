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
    val modelError: String? = null,
    val isApiSettingsOpen: Boolean = false,
    val groqApiKey: String = "",
    val geminiApiKey: String = "",
    val groqModel: String = "llama-3.3-70b-versatile",
    val geminiModel: String = "gemini-1.5-flash",
    val isGroqConfigured: Boolean = false,
    val isGeminiConfigured: Boolean = false,
    val apiTestStatus: String? = null,
    val isTestingApi: Boolean = false
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val progressRepository: ProgressRepository,
    private val vocabularyRepository: VocabularyRepository,
    private val aiResponseCache: AIResponseCache,
    private val modelManager: ModelManager,
    private val apiConfigStore: com.vaniflow.app.engine.ai.provider.ApiConfigStore,
    private val openAIAdapter: com.vaniflow.app.engine.ai.provider.adapter.OpenAICompatibleAdapter,
    private val geminiAdapter: com.vaniflow.app.engine.ai.provider.adapter.GeminiProviderAdapter
) : ViewModel() {

    constructor(
        sessionRepository: SessionRepository,
        progressRepository: ProgressRepository,
        vocabularyRepository: VocabularyRepository,
        aiResponseCache: AIResponseCache,
        modelManager: ModelManager
    ) : this(
        sessionRepository,
        progressRepository,
        vocabularyRepository,
        aiResponseCache,
        modelManager,
        com.vaniflow.app.engine.ai.provider.ApiConfigStore(),
        com.vaniflow.app.engine.ai.provider.adapter.OpenAICompatibleAdapter(),
        com.vaniflow.app.engine.ai.provider.adapter.GeminiProviderAdapter()
    )

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        observeProfileStats()
        loadModelStates()
        observeDownloads()
        loadApiConfig()
    }

    private fun loadApiConfig() {
        _uiState.update {
            it.copy(
                groqApiKey = apiConfigStore.getPrimaryApiKey(),
                geminiApiKey = apiConfigStore.getSecondaryApiKey(),
                groqModel = apiConfigStore.getPrimaryModel().ifBlank { "llama-3.3-70b-versatile" },
                geminiModel = apiConfigStore.getSecondaryModel().ifBlank { "gemini-1.5-flash" },
                isGroqConfigured = apiConfigStore.hasPrimaryCredentials(),
                isGeminiConfigured = apiConfigStore.hasSecondaryCredentials()
            )
        }
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

    fun openApiSettings() {
        loadApiConfig()
        _uiState.update { it.copy(isApiSettingsOpen = true, apiTestStatus = null) }
    }

    fun dismissApiSettings() {
        _uiState.update { it.copy(isApiSettingsOpen = false, apiTestStatus = null) }
    }

    fun updateGroqApiKey(key: String) {
        _uiState.update { it.copy(groqApiKey = key) }
    }

    fun updateGeminiApiKey(key: String) {
        _uiState.update { it.copy(geminiApiKey = key) }
    }

    fun updateGroqModel(model: String) {
        _uiState.update { it.copy(groqModel = model) }
    }

    fun updateGeminiModel(model: String) {
        _uiState.update { it.copy(geminiModel = model) }
    }

    fun saveApiSettings() {
        val state = _uiState.value
        apiConfigStore.setPrimaryConfig(
            apiKey = state.groqApiKey,
            endpoint = "https://api.groq.com/openai/v1/chat/completions",
            model = state.groqModel.ifBlank { "llama-3.3-70b-versatile" },
            adapterType = "openai_compatible"
        )
        apiConfigStore.setSecondaryConfig(
            apiKey = state.geminiApiKey,
            endpoint = "https://generativelanguage.googleapis.com/v1beta/models/${state.geminiModel.ifBlank { "gemini-1.5-flash" }}:generateContent",
            model = state.geminiModel.ifBlank { "gemini-1.5-flash" },
            adapterType = "gemini"
        )
        loadApiConfig()
        _uiState.update { it.copy(isApiSettingsOpen = false, apiTestStatus = null) }
    }

    fun testGroqConnection() {
        val key = _uiState.value.groqApiKey.trim()
        val model = _uiState.value.groqModel.trim().ifBlank { "llama-3.3-70b-versatile" }
        if (key.isBlank()) {
            _uiState.update { it.copy(apiTestStatus = "Please enter a Groq API key (starts with gsk_...)") }
            return
        }
        _uiState.update { it.copy(isTestingApi = true, apiTestStatus = "Testing Groq ($model)...") }
        viewModelScope.launch {
            val result = openAIAdapter.generate(
                endpoint = "https://api.groq.com/openai/v1/chat/completions",
                apiKey = key,
                model = model,
                systemPrompt = "You are a test assistant.",
                history = emptyList(),
                userInput = "Say hello in 3 words.",
                timeoutMs = 6000L
            )
            val status = when (result) {
                is com.vaniflow.app.engine.ai.AIResult.Success -> "Groq Connected successfully! (${result.metadata.latencyMs}ms): \"${result.text.take(40)}\""
                is com.vaniflow.app.engine.ai.AIResult.Error -> "Groq Connection Failed: ${result.message}"
            }
            _uiState.update { it.copy(isTestingApi = false, apiTestStatus = status) }
        }
    }

    fun testGeminiConnection() {
        val key = _uiState.value.geminiApiKey.trim()
        val model = _uiState.value.geminiModel.trim().ifBlank { "gemini-1.5-flash" }
        if (key.isBlank()) {
            _uiState.update { it.copy(apiTestStatus = "Please enter a Gemini API key (starts with AIzaSy...)") }
            return
        }
        _uiState.update { it.copy(isTestingApi = true, apiTestStatus = "Testing Gemini ($model)...") }
        viewModelScope.launch {
            val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent"
            val result = geminiAdapter.generate(
                endpoint = endpoint,
                apiKey = key,
                model = model,
                systemPrompt = "You are a test assistant.",
                history = emptyList(),
                userInput = "Say hello in 3 words.",
                timeoutMs = 6000L
            )
            val status = when (result) {
                is com.vaniflow.app.engine.ai.AIResult.Success -> "Gemini Connected successfully! (${result.metadata.latencyMs}ms): \"${result.text.take(40)}\""
                is com.vaniflow.app.engine.ai.AIResult.Error -> "Gemini Connection Failed: ${result.message}"
            }
            _uiState.update { it.copy(isTestingApi = false, apiTestStatus = status) }
        }
    }
}
