package com.vaniflow.app.data.repository

import com.vaniflow.app.data.local.db.dao.SessionDao
import com.vaniflow.app.data.local.db.entity.SessionEntity
import com.vaniflow.app.data.local.db.entity.SpeechAnalysisEntity
import com.vaniflow.app.domain.repository.ConceptMasteryRepository
import com.vaniflow.app.domain.repository.ImprovementStat
import com.vaniflow.app.domain.repository.LearnerProfileRepository
import com.vaniflow.app.domain.repository.ProgressData
import com.vaniflow.app.domain.repository.ProgressRepository
import com.vaniflow.app.domain.repository.SpeechAnalysisRepository
import com.vaniflow.app.domain.repository.VocabularyMemoryRepository
import com.vaniflow.app.domain.repository.WeeklyDayData
import com.vaniflow.app.engine.learning.tutor.LearnerProfile
import com.vaniflow.app.engine.learning.tutor.model.MasteryState
import com.vaniflow.app.engine.learning.tutor.model.VocabularyMemory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultProgressRepository @Inject constructor(
    private val sessionDao: SessionDao,
    private val learnerProfileRepository: LearnerProfileRepository,
    private val conceptMasteryRepository: ConceptMasteryRepository,
    private val vocabularyMemoryRepository: VocabularyMemoryRepository,
    private val speechAnalysisRepository: SpeechAnalysisRepository
) : ProgressRepository {

    constructor(sessionDao: SessionDao) : this(
        sessionDao = sessionDao,
        learnerProfileRepository = object : LearnerProfileRepository {
            private val flow = kotlinx.coroutines.flow.MutableStateFlow(LearnerProfile())
            override fun observeLearnerProfile(): Flow<LearnerProfile?> = flow
            override suspend fun getLearnerProfile(): LearnerProfile = flow.value
            override suspend fun saveLearnerProfile(profile: LearnerProfile) { flow.value = profile }
            override suspend fun clearLearnerProfile() { flow.value = LearnerProfile() }
        },
        conceptMasteryRepository = object : ConceptMasteryRepository {
            override fun getAllMasteryFlow() = kotlinx.coroutines.flow.flowOf(emptyList<MasteryState>())
            override suspend fun getAllMastery() = emptyList<MasteryState>()
            override suspend fun getMastery(conceptId: String) = null
            override suspend fun saveMastery(mastery: MasteryState) {}
            override suspend fun saveAllMastery(list: List<MasteryState>) {}
            override suspend fun clearAllMastery() {}
        },
        vocabularyMemoryRepository = object : VocabularyMemoryRepository {
            override fun getAllVocabularyMemoryFlow() = kotlinx.coroutines.flow.flowOf(emptyList<VocabularyMemory>())
            override suspend fun getExpressionsNeedingPractice() = emptyList<VocabularyMemory>()
            override suspend fun saveExpression(memory: VocabularyMemory) {}
            override suspend fun deleteExpression(id: String) {}
            override suspend fun clearAllVocabularyMemory() {}
        },
        speechAnalysisRepository = object : SpeechAnalysisRepository {
            override fun getAllSpeechAnalysisFlow(): Flow<List<SpeechAnalysisEntity>> = kotlinx.coroutines.flow.flowOf(emptyList())
            override suspend fun getSpeechAnalysisForSession(sessionId: String): List<SpeechAnalysisEntity> = emptyList()
            override suspend fun getSpeechAnalysisForTurn(turnId: String): SpeechAnalysisEntity? = null
            override suspend fun recordSpeechAnalysis(entity: SpeechAnalysisEntity) {}
            override suspend fun recordAllSpeechAnalysis(list: List<SpeechAnalysisEntity>) {}
            override suspend fun clearAllSpeechAnalysis() {}
        }
    )

    override fun getProgress(): Flow<ProgressData> {
        return combine(
            sessionDao.getAllSessions(),
            learnerProfileRepository.observeLearnerProfile(),
            conceptMasteryRepository.getAllMasteryFlow(),
            vocabularyMemoryRepository.getAllVocabularyMemoryFlow(),
            speechAnalysisRepository.getAllSpeechAnalysisFlow()
        ) { sessions, profile, masteryList, vocabList, speechList ->
            computeProgressData(sessions, profile ?: LearnerProfile(), masteryList, vocabList, speechList)
        }
    }

    override fun calculateStreak(
        sessionTimestamps: List<Long>,
        nowEpochMs: Long
    ): Pair<Int, Int> {
        if (sessionTimestamps.isEmpty()) return Pair(0, 0)

        val zoneId = ZoneId.systemDefault()
        val today = Instant.ofEpochMilli(nowEpochMs).atZone(zoneId).toLocalDate()
        val yesterday = today.minusDays(1)

        val practiceDates = sessionTimestamps
            .map { Instant.ofEpochMilli(it).atZone(zoneId).toLocalDate() }
            .distinct()
            .sortedDescending()

        // 1. Current Streak
        var currentStreak = 0
        val startDate = when {
            practiceDates.contains(today) -> today
            practiceDates.contains(yesterday) -> yesterday
            else -> null
        }

        if (startDate != null) {
            var checkDate: LocalDate = startDate
            while (practiceDates.contains(checkDate)) {
                currentStreak++
                checkDate = checkDate.minusDays(1)
            }
        }

        // 2. Longest Streak
        var longestStreak = 0
        var tempStreak = 0
        val sortedAscending = practiceDates.sorted()

        for (i in sortedAscending.indices) {
            if (i == 0) {
                tempStreak = 1
            } else {
                val prev = sortedAscending[i - 1]
                val curr = sortedAscending[i]
                if (curr == prev.plusDays(1)) {
                    tempStreak++
                } else {
                    if (tempStreak > longestStreak) longestStreak = tempStreak
                    tempStreak = 1
                }
            }
        }
        if (tempStreak > longestStreak) longestStreak = tempStreak

        return Pair(currentStreak, longestStreak)
    }

    private fun computeProgressData(
        sessions: List<SessionEntity>,
        profile: LearnerProfile,
        masteryList: List<MasteryState>,
        vocabList: List<VocabularyMemory>,
        speechList: List<SpeechAnalysisEntity>
    ): ProgressData {
        if (sessions.isEmpty()) {
            val levelDisplay = if (profile.totalUtterances >= 15) {
                profile.estimatedLevel.displayLabel
            } else {
                "Building level (${profile.totalUtterances}/15 turns)"
            }

            return ProgressData(
                totalMinutes = 0,
                sessionCount = 0,
                currentStreak = 0,
                longestStreak = 0,
                weeklyData = generateEmptyWeek(),
                averageFluency = 0,
                averageGrammar = 0,
                averagePronunciation = 0,
                averageVocabulary = 0,
                improvements = emptyList(),
                aiCoachRecommendation = "Start your first conversation to build your speaking baseline!",
                estimatedLevel = levelDisplay,
                speakingConfidenceScore = profile.speakingConfidenceScore,
                masteredConceptsCount = 0,
                conceptsNeedingPracticeCount = 0,
                vocabularyCount = 0,
                conceptsNeedingPractice = emptyList(),
                averageSpeakingRateWpm = 0,
                pronunciationEvidenceState = "Building baseline...",
                pronunciationPracticeAreas = emptyList(),
                speechConsistencyNote = "Ready to start"
            )
        }

        val totalMinutes = sessions.sumOf { it.speakingTimeMinutes }
        val sessionCount = sessions.size
        val timestamps = sessions.map { it.startTimeMs }
        val (currentStreak, longestStreak) = calculateStreak(timestamps)

        // Real Averages
        val avgFluency = (sessions.sumOf { it.fluencyScore } / sessionCount).coerceIn(0, 100)
        val avgGrammar = (sessions.sumOf { it.grammarScore } / sessionCount).coerceIn(0, 100)
        val avgPronunciation = (sessions.sumOf { it.pronunciationScore } / sessionCount).coerceIn(0, 100)
        val avgVocabulary = (sessions.sumOf { it.vocabularyScore } / sessionCount).coerceIn(0, 100)

        // Weekly breakdown (Current calendar week Monday to Sunday)
        val zoneId = ZoneId.systemDefault()
        val today = LocalDate.now(zoneId)
        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

        val daysOfWeek = (0..6).map { monday.plusDays(it.toLong()) }
        val maxDayMinutes = daysOfWeek.maxOfOrNull { date ->
            sessions.filter {
                Instant.ofEpochMilli(it.startTimeMs).atZone(zoneId).toLocalDate() == date
            }.sumOf { it.speakingTimeMinutes }
        }?.coerceAtLeast(1) ?: 1

        val weeklyData = daysOfWeek.map { date ->
            val daySessions = sessions.filter {
                Instant.ofEpochMilli(it.startTimeMs).atZone(zoneId).toLocalDate() == date
            }
            val dayMinutes = daySessions.sumOf { it.speakingTimeMinutes }
            val fraction = (dayMinutes.toFloat() / maxDayMinutes.toFloat()).coerceIn(0.15f, 1.0f)
            val dayLabel = date.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercaseChar() }

            WeeklyDayData(
                dayLabel = dayLabel,
                minutes = dayMinutes,
                sessionCount = daySessions.size,
                fraction = if (dayMinutes > 0) fraction else 0.15f,
                isToday = (date == today)
            )
        }

        // Evidence-based improvements
        val improvements = mutableListOf<ImprovementStat>()
        val mastered = masteryList.filter { it.isMastered }
        if (mastered.isNotEmpty()) {
            improvements.add(
                ImprovementStat(
                    label = "Mastered concepts",
                    changeText = "${mastered.size} concepts solid",
                    isPositive = true
                )
            )
        }
        if (profile.successfulRetries > 0) {
            improvements.add(
                ImprovementStat(
                    label = "Self-corrections",
                    changeText = "${profile.successfulRetries} successful retries",
                    isPositive = true
                )
            )
        }
        if (vocabList.isNotEmpty()) {
            improvements.add(
                ImprovementStat(
                    label = "Spoken expressions",
                    changeText = "${vocabList.size} expressions practiced",
                    isPositive = true
                )
            )
        }
        if (improvements.isEmpty()) {
            improvements.add(
                ImprovementStat(
                    label = "Speaking fluency",
                    changeText = "Practicing consistently",
                    isPositive = true
                )
            )
        }

        // Speech intelligence aggregation
        val validSpeech = speechList.filter { it.hasPhonemeEvidence }
        val avgWpm = if (speechList.isNotEmpty()) {
            val nonZero = speechList.filter { it.wordsPerMinute > 0 }
            if (nonZero.isNotEmpty()) nonZero.map { it.wordsPerMinute }.average().toInt() else 0
        } else 0

        val practiceAreas = speechList
            .mapNotNull { it.practicedSound }
            .distinct()
            .map { it.replace('_', ' ').replaceFirstChar { c -> c.uppercaseChar() } }

        val pronState = when {
            speechList.isEmpty() -> "Building baseline..."
            validSpeech.isEmpty() -> "Not enough evidence yet"
            validSpeech.any { it.qualitativePronunciation == "NATURAL" } -> "Natural Pronunciation"
            validSpeech.any { it.qualitativePronunciation == "CLEAR" } -> "Clear Pronunciation"
            else -> "Developing Clarity"
        }

        val speechNote = when {
            avgWpm in 100..160 -> "Natural Conversational Pacing ($avgWpm WPM)"
            avgWpm > 160 -> "Fast Pacing ($avgWpm WPM)"
            avgWpm in 50..99 -> "Deliberate Pacing ($avgWpm WPM)"
            else -> "Comfortable Pacing"
        }

        val latestSession = sessions.firstOrNull()
        val recommendation = latestSession?.focusNext ?: "Practice daily conversations to maintain your speaking momentum!"

        val levelDisplay = if (profile.totalUtterances >= 15) {
            profile.estimatedLevel.displayLabel
        } else {
            "Building level (${profile.totalUtterances}/15 turns)"
        }

        return ProgressData(
            totalMinutes = totalMinutes,
            sessionCount = sessionCount,
            currentStreak = currentStreak,
            longestStreak = longestStreak,
            weeklyData = weeklyData,
            averageFluency = avgFluency,
            averageGrammar = avgGrammar,
            averagePronunciation = avgPronunciation,
            averageVocabulary = avgVocabulary,
            improvements = improvements,
            aiCoachRecommendation = recommendation,
            estimatedLevel = levelDisplay,
            speakingConfidenceScore = profile.speakingConfidenceScore,
            masteredConceptsCount = masteryList.count { it.isMastered },
            conceptsNeedingPracticeCount = masteryList.count { it.needsPractice },
            vocabularyCount = vocabList.size,
            conceptsNeedingPractice = masteryList.filter { it.needsPractice }.map { it.conceptId },
            averageSpeakingRateWpm = avgWpm,
            pronunciationEvidenceState = pronState,
            pronunciationPracticeAreas = practiceAreas,
            speechConsistencyNote = speechNote
        )
    }

    private fun generateEmptyWeek(): List<WeeklyDayData> {
        val today = LocalDate.now(ZoneId.systemDefault())
        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

        return (0..6).map { offset ->
            val date = monday.plusDays(offset.toLong())
            val label = date.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercaseChar() }
            WeeklyDayData(
                dayLabel = label,
                minutes = 0,
                sessionCount = 0,
                fraction = 0.15f,
                isToday = (date == today)
            )
        }
    }
}
