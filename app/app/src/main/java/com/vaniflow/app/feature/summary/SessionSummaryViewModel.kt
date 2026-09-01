package com.vaniflow.app.feature.summary

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaniflow.app.domain.repository.LearningEventRepository
import com.vaniflow.app.domain.repository.SessionRepository
import com.vaniflow.app.domain.repository.SpeechAnalysisRepository
import com.vaniflow.app.engine.learning.tutor.model.LearningEventType
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
    val strongestArea: String = "Speaking Fluency",
    val focusNext: String = "Natural Phrasing",
    val focusNextExplanation: String = "Practice using natural phrasing in upcoming conversations.",
    val successfulRetriesCount: Int = 0,
    val correctionsCount: Int = 0,
    val improvedConcepts: List<String> = emptyList(),
    val weakConcepts: List<String> = emptyList(),
    val learnedExpressions: List<String> = emptyList(),
    val pronunciationState: String = "Not enough evidence yet",
    val averageWordsPerMinute: Int = 0,
    val totalPausesCount: Int = 0,
    val practicedSounds: List<String> = emptyList()
)

@HiltViewModel
class SessionSummaryViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val learningEventRepository: LearningEventRepository,
    private val speechAnalysisRepository: SpeechAnalysisRepository,
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

            val events = try {
                learningEventRepository.getEventsForSession(sessionId)
            } catch (_: Exception) {
                emptyList()
            }

            val speechAnalyses = try {
                speechAnalysisRepository.getSpeechAnalysisForSession(sessionId)
            } catch (_: Exception) {
                emptyList()
            }

            val successfulRetries = events.count { it.type == LearningEventType.SUCCESSFUL_RETRY }
            val corrections = events.count { it.type == LearningEventType.CORRECTION }
            val improved = events.filter { it.type == LearningEventType.SUCCESSFUL_RETRY || it.type == LearningEventType.MASTERY_GAIN || it.type == LearningEventType.PRONUNCIATION_IMPROVEMENT }
                .map { it.conceptId.replace('_', ' ').replaceFirstChar { c -> c.uppercaseChar() } }
                .distinct()
            val weak = events.filter { it.type == LearningEventType.FAILED_RETRY || it.type == LearningEventType.CORRECTION }
                .map { it.conceptId.replace('_', ' ').replaceFirstChar { c -> c.uppercaseChar() } }
                .distinct()
            val expressions = events.filter { it.type == LearningEventType.VOCABULARY_LEARNED }
                .map { it.conceptId }
                .distinct()

            val totalPauses = speechAnalyses.sumOf { it.pauseCount }
            val avgWpm = if (speechAnalyses.isNotEmpty()) {
                val nonZero = speechAnalyses.filter { it.wordsPerMinute > 0 }
                if (nonZero.isNotEmpty()) nonZero.map { it.wordsPerMinute }.average().toInt() else 0
            } else 0

            val practicedSounds = speechAnalyses
                .mapNotNull { it.practicedSound }
                .distinct()
                .map { it.replace('_', ' ').replaceFirstChar { c -> c.uppercaseChar() } }

            val pronState = when {
                speechAnalyses.isEmpty() -> "Not enough pronunciation evidence"
                practicedSounds.isNotEmpty() -> "Practice Target: " + practicedSounds.first()
                speechAnalyses.any { it.qualitativePronunciation == "NATURAL" } -> "Audio Clarity: Natural"
                speechAnalyses.any { it.qualitativePronunciation == "CLEAR" } -> "Audio Clarity: Clear"
                else -> "Not enough pronunciation evidence"
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
                            focusNextExplanation = "Focus on practicing ${session.focusNext.lowercase()} in your upcoming sessions.",
                            successfulRetriesCount = successfulRetries,
                            correctionsCount = corrections,
                            improvedConcepts = improved,
                            weakConcepts = weak,
                            learnedExpressions = expressions,
                            pronunciationState = pronState,
                            averageWordsPerMinute = avgWpm,
                            totalPausesCount = totalPauses,
                            practicedSounds = practicedSounds
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            successfulRetriesCount = successfulRetries,
                            correctionsCount = corrections,
                            improvedConcepts = improved,
                            weakConcepts = weak,
                            learnedExpressions = expressions,
                            pronunciationState = pronState,
                            averageWordsPerMinute = avgWpm,
                            totalPausesCount = totalPauses,
                            practicedSounds = practicedSounds
                        )
                    }
                }
            }
        }
    }
}
