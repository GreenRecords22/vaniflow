package com.vaniflow.app.feature.summary

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaniflow.app.domain.repository.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SessionSummaryUiState(
    val isLoading: Boolean = true,
    val sessionId: String = "",
    val speakingTimeMinutes: Int = 0,
    val fluencyScore: Int = 0,
    val grammarScore: Int = 0,
    val pronunciationScore: Int = 0,
    val vocabularyScore: Int = 0,
    val strongestArea: String = "Pronunciation",
    val focusNext: String = "Past Tense",
    val focusNextExplanation: String = "Practice using past tense verbs when sharing experiences."
)

@HiltViewModel
class SessionSummaryViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val sessionId: String = savedStateHandle["sessionId"] ?: ""

    private val _uiState = MutableStateFlow(SessionSummaryUiState(sessionId = sessionId))
    val uiState: StateFlow<SessionSummaryUiState> = _uiState.asStateFlow()

    init {
        loadSessionData()
    }

    private fun loadSessionData() {
        viewModelScope.launch {
            if (sessionId.isBlank()) {
                _uiState.update { it.copy(isLoading = false) }
                return@launch
            }

            sessionRepository.getSessionById(sessionId).collect { session ->
                if (session != null) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            speakingTimeMinutes = session.speakingTimeMinutes,
                            fluencyScore = session.fluencyScore,
                            grammarScore = session.grammarScore,
                            pronunciationScore = session.pronunciationScore,
                            vocabularyScore = session.vocabularyScore,
                            strongestArea = session.strongestArea,
                            focusNext = session.focusNext,
                            focusNextExplanation = "Focus on practicing ${session.focusNext.lowercase()} in your upcoming sessions."
                        )
                    }
                } else {
                    _uiState.update { it.copy(isLoading = false) }
                }
            }
        }
    }
}
