package com.vaniflow.app.feature.character

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaniflow.app.domain.repository.UserPreferencesRepository
import com.vaniflow.app.engine.tts.TTSEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CharacterSelectionViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val ttsEngine: TTSEngine
) : ViewModel() {

    val selectedCharacterId: StateFlow<String> = userPreferencesRepository.selectedCharacterId

    private val _playingCharacterId = MutableStateFlow<String?>(null)
    val playingCharacterId: StateFlow<String?> = _playingCharacterId.asStateFlow()

    fun selectCharacter(characterId: String) {
        viewModelScope.launch {
            userPreferencesRepository.setSelectedCharacterId(characterId)
        }
    }

    fun playVoiceSample(characterId: String) {
        viewModelScope.launch {
            if (_playingCharacterId.value == characterId) {
                ttsEngine.stop()
                _playingCharacterId.value = null
                return@launch
            }

            ttsEngine.stop()
            _playingCharacterId.value = characterId

            val sampleText = when (characterId.lowercase()) {
                "raya" -> "Hi there! I am Raya. I love helping you practice English speaking with patience and joy."
                "rudra" -> "Hey! I am Rudra. Let's practice dynamic and energetic English conversations together!"
                "adwaita" -> "Hello. I am Adwaita. I am here to help you speak fluent, professional English with confidence."
                "shub" -> "Hello. I am Shub. Together, we will build calm, clear, and analytical communication skills."
                else -> "Hello! I am your AI conversation partner."
            }

            ttsEngine.speak(sampleText, characterId, 1.0f)
            _playingCharacterId.value = null
        }
    }
}
