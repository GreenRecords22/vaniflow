package com.vaniflow.app.feature.conversation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaniflow.app.domain.model.Character
import com.vaniflow.app.domain.model.ConversationTurn
import com.vaniflow.app.domain.model.Scenario
import com.vaniflow.app.domain.model.ConversationState
import com.vaniflow.app.engine.audio.AudioRecorder
import com.vaniflow.app.engine.audio.VADEngine
import com.vaniflow.app.engine.character.CharacterRegistry
import com.vaniflow.app.engine.conversation.ConversationEngine
import com.vaniflow.app.engine.scenario.ScenarioRegistry
import com.vaniflow.app.engine.stt.STTEngine
import com.vaniflow.app.engine.stt.STTResult
import com.vaniflow.app.ui.avatar.AvatarController
import com.vaniflow.app.ui.avatar.AvatarState
import com.vaniflow.app.ui.avatar.LipSyncController
import com.vaniflow.app.ui.avatar.VisemeLipSyncController
import com.vaniflow.app.ui.avatar.model.AvatarEmotion
import com.vaniflow.app.ui.avatar.model.EmotionParser
import com.vaniflow.app.ui.avatar.toAvatarState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

import com.vaniflow.app.ui.avatar.MouthShape
import com.vaniflow.app.ui.avatar.viseme.Viseme

data class ConversationUiState(
    val character: Character? = null,
    val scenario: Scenario? = null,
    val state: ConversationState = ConversationState.IDLE,
    val avatarState: AvatarState = AvatarState.IDLE,
    val currentEmotion: AvatarEmotion = AvatarEmotion.NEUTRAL,
    val mouthOpenness: Float = 0.0f,
    val currentViseme: Viseme = Viseme.REST,
    val currentMouthShape: MouthShape = MouthShape.REST,
    val turns: List<ConversationTurn> = emptyList(),
    val partialTranscript: String = "",
    val errorMessage: String? = null,
    val requiresPermissionRationale: Boolean = false,
    val requiresPermissionSettings: Boolean = false,
    val isHandsFreeActive: Boolean = true
)

private object ConversationErrorMessages {
    const val MIC_REQUIRED = "Microphone access is needed for speaking practice."
    const val MODEL_NOT_READY = "Your offline speaking model isn't ready yet."
    const val TEMPORARY_AI = "Let's try that again."
    const val AUDIO_FAILED = "I couldn't play the response. Tap to retry."
    const val NO_SPEECH = "I didn't hear you. Try speaking again."
    const val STT_FAILED = "I didn't catch that. Tap the mic and try again."
}

@HiltViewModel
class ConversationViewModel @Inject constructor(
    private val conversationEngine: ConversationEngine,
    private val audioRecorder: AudioRecorder,
    private val vadEngine: VADEngine,
    private val sttEngine: STTEngine,
    private val characterRegistry: CharacterRegistry,
    private val scenarioRegistry: ScenarioRegistry,
    savedStateHandle: SavedStateHandle,
    private val avatarController: AvatarController,
    private val lipSyncController: LipSyncController
) : ViewModel() {

    constructor(
        conversationEngine: ConversationEngine,
        audioRecorder: AudioRecorder,
        vadEngine: VADEngine,
        sttEngine: STTEngine,
        characterRegistry: CharacterRegistry,
        scenarioRegistry: ScenarioRegistry,
        savedStateHandle: SavedStateHandle
    ) : this(
        conversationEngine,
        audioRecorder,
        vadEngine,
        sttEngine,
        characterRegistry,
        scenarioRegistry,
        savedStateHandle,
        AvatarController(),
        VisemeLipSyncController()
    )

    private val characterId: String = checkNotNull(savedStateHandle["characterId"])
    private val scenarioId: String = checkNotNull(savedStateHandle["scenarioId"])

    private val _uiState = MutableStateFlow(ConversationUiState(isHandsFreeActive = true))
    val uiState: StateFlow<ConversationUiState> = _uiState.asStateFlow()

    private var sttJob: Job? = null
    private var isHandsFreeActive = true

    private var consecutiveSilentReArms = 0
    private val maxSilentReArms = 15

    init {
        val character = characterRegistry.getCharacter(characterId)
        val scenario = scenarioRegistry.getScenario(scenarioId)

        _uiState.update { it.copy(character = character, scenario = scenario) }
        conversationEngine.startSession(character, scenario)

        viewModelScope.launch {
            conversationEngine.state.collect { engineState ->
                val lastAssistantTurn = _uiState.value.turns.lastOrNull { it.speaker == ConversationTurn.Speaker.AI }
                val emotion = if (lastAssistantTurn != null) EmotionParser.parseFromText(lastAssistantTurn.text).emotion else AvatarEmotion.NEUTRAL
                avatarController.update(engineState, emotion)
                _uiState.update {
                    it.copy(
                        state = engineState,
                        avatarState = engineState.toAvatarState(emotion),
                        currentEmotion = emotion
                    )
                }
                if (engineState == ConversationState.LISTENING && isHandsFreeActive && sttJob?.isActive != true) {
                    startAudioCaptureAndListening()
                }
            }
        }

        viewModelScope.launch {
            avatarController.avatarState.collect { aState ->
                _uiState.update { it.copy(avatarState = aState) }
            }
        }

        viewModelScope.launch {
            avatarController.currentEmotion.collect { emo ->
                _uiState.update { it.copy(currentEmotion = emo) }
            }
        }

        viewModelScope.launch {
            lipSyncController.mouthOpennessFlow.collect { openness ->
                _uiState.update { it.copy(mouthOpenness = openness) }
            }
        }

        viewModelScope.launch {
            lipSyncController.visemeFlow.collect { vState ->
                _uiState.update { it.copy(currentViseme = vState.viseme) }
            }
        }

        viewModelScope.launch {
            lipSyncController.mouthShapeFlow.collect { mShape ->
                _uiState.update { it.copy(currentMouthShape = mShape) }
            }
        }

        viewModelScope.launch {
            conversationEngine.turns.collect { turnsList ->
                _uiState.update { it.copy(turns = turnsList) }
                val lastTurn = turnsList.lastOrNull()
                if (lastTurn != null && lastTurn.speaker == ConversationTurn.Speaker.AI) {
                    val metadata = EmotionParser.parseFromText(lastTurn.text)
                    avatarController.setEmotion(metadata.emotion)
                }
            }
        }

        viewModelScope.launch {
            conversationEngine.errorMessage.collect { message ->
                if (message != null) {
                    _uiState.update { it.copy(errorMessage = message) }
                }
            }
        }

        viewModelScope.launch {
            conversationEngine.playInitialGreeting()
        }
    }

    fun onMicTapped() {
        val currentState = _uiState.value.state
        when (currentState) {
            ConversationState.AI_SPEAKING -> {
                isHandsFreeActive = true
                consecutiveSilentReArms = 0
                lipSyncController.interrupt()
                avatarController.update(ConversationState.INTERRUPTED)
                _uiState.update { it.copy(isHandsFreeActive = true, avatarState = AvatarState.INTERRUPTED) }
                viewModelScope.launch {
                    conversationEngine.interrupt()
                    startAudioCaptureAndListening()
                }
            }
            ConversationState.LISTENING, ConversationState.USER_SPEAKING -> {
                isHandsFreeActive = false
                lipSyncController.interrupt()
                avatarController.update(ConversationState.IDLE)
                _uiState.update { it.copy(isHandsFreeActive = false, state = ConversationState.IDLE, avatarState = AvatarState.IDLE) }
                stopAudioCapture()
            }
            else -> {
                isHandsFreeActive = true
                consecutiveSilentReArms = 0
                avatarController.update(ConversationState.LISTENING)
                _uiState.update { it.copy(isHandsFreeActive = true, errorMessage = null, avatarState = AvatarState.LISTENING) }
                startAudioCaptureAndListening()
            }
        }
    }

    fun onPermissionGranted() {
        if (isHandsFreeActive && _uiState.value.state == ConversationState.IDLE) {
            consecutiveSilentReArms = 0
            startAudioCaptureAndListening()
        }
    }

    fun onPermissionDenied() {
        _uiState.update {
            it.copy(
                requiresPermissionRationale = true,
                errorMessage = ConversationErrorMessages.MIC_REQUIRED
            )
        }
    }

    fun onPermissionPermanentlyDenied() {
        _uiState.update {
            it.copy(
                requiresPermissionRationale = false,
                requiresPermissionSettings = true,
                errorMessage = ConversationErrorMessages.MIC_REQUIRED
            )
        }
    }

    fun dismissPermissionRationale() {
        _uiState.update { it.copy(requiresPermissionRationale = false) }
    }

    fun dismissPermissionSettings() {
        _uiState.update { it.copy(requiresPermissionSettings = false) }
    }

    fun clearErrorMessage() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    private fun startAudioCaptureAndListening() {
        sttJob?.cancel()
        conversationEngine.onUserStartedSpeaking()
        avatarController.update(ConversationState.LISTENING)
        _uiState.update {
            it.copy(
                state = ConversationState.LISTENING,
                avatarState = AvatarState.LISTENING,
                partialTranscript = ""
            )
        }

        sttJob = viewModelScope.launch {
            sttEngine.startListening()
                .catch { cause ->
                    _uiState.update {
                        it.copy(
                            state = ConversationState.IDLE,
                            avatarState = AvatarState.IDLE,
                            errorMessage = ConversationErrorMessages.STT_FAILED
                        )
                    }
                }
                .collect { result ->
                    when (result) {
                        is STTResult.Partial -> {
                            _uiState.update {
                                it.copy(
                                    state = ConversationState.USER_SPEAKING,
                                    avatarState = AvatarState.LISTENING,
                                    partialTranscript = result.text,
                                    errorMessage = null
                                )
                            }
                        }
                        is STTResult.Final -> {
                            val userText = result.text.trim()
                            if (userText.isNotBlank()) {
                                consecutiveSilentReArms = 0
                                avatarController.update(ConversationState.THINKING)
                                _uiState.update {
                                    it.copy(
                                        state = ConversationState.THINKING,
                                        avatarState = AvatarState.THINKING,
                                        partialTranscript = userText,
                                        errorMessage = null
                                    )
                                }
                                conversationEngine.processUserUtterance(userText)
                            } else {
                                if (isHandsFreeActive && _uiState.value.state != ConversationState.AI_SPEAKING && _uiState.value.state != ConversationState.THINKING) {
                                    consecutiveSilentReArms++
                                    if (consecutiveSilentReArms <= maxSilentReArms) {
                                        delay(200)
                                        startAudioCaptureAndListening()
                                    } else {
                                        consecutiveSilentReArms = 0
                                        avatarController.update(ConversationState.IDLE)
                                        _uiState.update { it.copy(state = ConversationState.IDLE, avatarState = AvatarState.IDLE) }
                                    }
                                } else {
                                    avatarController.update(ConversationState.IDLE)
                                    _uiState.update {
                                        it.copy(
                                            state = ConversationState.IDLE,
                                            avatarState = AvatarState.IDLE,
                                            errorMessage = ConversationErrorMessages.NO_SPEECH
                                        )
                                    }
                                }
                            }
                            _uiState.update { it.copy(partialTranscript = "") }
                        }
                        is STTResult.Error -> {
                            if (isHandsFreeActive && _uiState.value.state != ConversationState.AI_SPEAKING && _uiState.value.state != ConversationState.THINKING) {
                                _uiState.update { it.copy(errorMessage = ConversationErrorMessages.STT_FAILED) }
                                consecutiveSilentReArms++
                                if (consecutiveSilentReArms <= maxSilentReArms) {
                                    delay(300)
                                    startAudioCaptureAndListening()
                                } else {
                                    consecutiveSilentReArms = 0
                                    avatarController.update(ConversationState.IDLE)
                                    _uiState.update { it.copy(state = ConversationState.IDLE, avatarState = AvatarState.IDLE, errorMessage = null) }
                                }
                            } else {
                                avatarController.update(ConversationState.IDLE)
                                _uiState.update {
                                    it.copy(
                                        state = ConversationState.IDLE,
                                        avatarState = AvatarState.IDLE,
                                        errorMessage = ConversationErrorMessages.STT_FAILED
                                    )
                                }
                            }
                        }
                    }
                }
        }
    }

    private fun stopAudioCapture() {
        sttJob?.cancel()
        sttJob = null
        viewModelScope.launch {
            try {
                sttEngine.stopListening()
            } catch (_: Exception) {}
        }
    }

    fun onEndSession(onComplete: (sessionId: String) -> Unit) {
        isHandsFreeActive = false
        lipSyncController.interrupt()
        avatarController.reset()
        stopAudioCapture()
        conversationEngine.endSession()
        onComplete(conversationEngine.getCurrentSessionId())
    }

    override fun onCleared() {
        super.onCleared()
        isHandsFreeActive = false
        lipSyncController.release()
        avatarController.reset()
        stopAudioCapture()
    }
}