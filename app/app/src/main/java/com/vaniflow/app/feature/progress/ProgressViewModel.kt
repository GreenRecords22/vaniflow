package com.vaniflow.app.feature.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaniflow.app.domain.repository.ImprovementStat
import com.vaniflow.app.domain.repository.ProgressData
import com.vaniflow.app.domain.repository.ProgressRepository
import com.vaniflow.app.domain.repository.WeeklyDayData
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProgressUiState(
    val isLoading: Boolean = false,
    val totalMinutes: Int = 0,
    val sessionCount: Int = 0,
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val weeklyData: List<WeeklyDayData> = emptyList(),
    val averageFluency: Int = 0,
    val averageGrammar: Int = 0,
    val averagePronunciation: Int = 0,
    val averageVocabulary: Int = 0,
    val improvements: List<ImprovementStat> = emptyList(),
    val aiCoachRecommendation: String = "Practice a 5-minute conversation today!",
    val estimatedLevel: String = "Building level...",
    val speakingConfidenceScore: Float = 65.0f,
    val masteredConceptsCount: Int = 0,
    val conceptsNeedingPracticeCount: Int = 0,
    val vocabularyCount: Int = 0,
    val conceptsNeedingPractice: List<String> = emptyList()
)

@HiltViewModel
class ProgressViewModel @Inject constructor(
    private val progressRepository: ProgressRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProgressUiState(isLoading = true))
    val uiState: StateFlow<ProgressUiState> = _uiState.asStateFlow()

    init {
        observeProgress()
    }

    private fun observeProgress() {
        viewModelScope.launch {
            progressRepository.getProgress().collect { data ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        totalMinutes = data.totalMinutes,
                        sessionCount = data.sessionCount,
                        currentStreak = data.currentStreak,
                        longestStreak = data.longestStreak,
                        weeklyData = data.weeklyData,
                        averageFluency = data.averageFluency,
                        averageGrammar = data.averageGrammar,
                        averagePronunciation = data.averagePronunciation,
                        averageVocabulary = data.averageVocabulary,
                        improvements = data.improvements,
                        aiCoachRecommendation = data.aiCoachRecommendation,
                        estimatedLevel = data.estimatedLevel,
                        speakingConfidenceScore = data.speakingConfidenceScore,
                        masteredConceptsCount = data.masteredConceptsCount,
                        conceptsNeedingPracticeCount = data.conceptsNeedingPracticeCount,
                        vocabularyCount = data.vocabularyCount,
                        conceptsNeedingPractice = data.conceptsNeedingPractice
                    )
                }
            }
        }
    }
}
