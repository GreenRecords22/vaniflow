package com.vaniflow.app.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaniflow.app.domain.model.Character
import com.vaniflow.app.domain.repository.ProgressRepository
import com.vaniflow.app.domain.repository.UserPreferencesRepository
import com.vaniflow.app.engine.character.CharacterRegistry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val selectedCharacter: Character? = null,
    val streakDays: Int = 0,
    val totalSpeakingMinutes: Int = 0,
    val sessionsCount: Int = 0
) {
    fun getSafeCharacter(registry: CharacterRegistry): Character {
        return selectedCharacter ?: registry.characters.first()
    }
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val progressRepository: ProgressRepository,
    private val characterRegistry: CharacterRegistry
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        HomeUiState(selectedCharacter = characterRegistry.characters.first())
    )
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        observeState()
    }

    private fun observeState() {
        viewModelScope.launch {
            combine(
                userPreferencesRepository.selectedCharacterId,
                progressRepository.getProgress()
            ) { characterId, progress ->
                val char = characterRegistry.getCharacter(characterId)
                HomeUiState(
                    selectedCharacter = char,
                    streakDays = progress.currentStreak,
                    totalSpeakingMinutes = progress.totalMinutes,
                    sessionsCount = progress.sessionCount
                )
            }.collect { newState ->
                _uiState.value = newState
            }
        }
    }

    fun selectCharacter(characterId: String) {
        viewModelScope.launch {
            userPreferencesRepository.setSelectedCharacterId(characterId)
        }
    }
}
