package com.vaniflow.app.engine.learning.tutor

import com.vaniflow.app.data.local.db.entity.SpeechAnalysisEntity
import com.vaniflow.app.domain.model.Scenario
import com.vaniflow.app.domain.repository.ConceptMasteryRepository
import com.vaniflow.app.domain.repository.LearnerProfileRepository
import com.vaniflow.app.domain.repository.LearningEventRepository
import com.vaniflow.app.domain.repository.SpeechAnalysisRepository
import com.vaniflow.app.domain.repository.VocabularyMemoryRepository
import com.vaniflow.app.engine.learning.tutor.model.CorrectionPolicyState
import com.vaniflow.app.engine.learning.tutor.model.DifficultyLevel
import com.vaniflow.app.engine.learning.tutor.model.LearningEvent
import com.vaniflow.app.engine.learning.tutor.model.LearningEventType
import com.vaniflow.app.engine.learning.tutor.model.LearningGoal
import com.vaniflow.app.engine.learning.tutor.model.MasteryState
import com.vaniflow.app.engine.learning.tutor.model.SessionLearningSummary
import com.vaniflow.app.engine.learning.tutor.model.VocabularyMemory
import com.vaniflow.app.engine.speech.model.FluencyAnalysisResult
import com.vaniflow.app.engine.speech.model.HesitationType
import com.vaniflow.app.engine.speech.model.PronunciationEvidence
import com.vaniflow.app.engine.speech.model.QualitativeFluencyRating
import com.vaniflow.app.engine.speech.model.QualitativePronunciationRating
import com.vaniflow.app.engine.speech.model.SpeechQualityResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages long-term learner intelligence, concept mastery progression, adaptive difficulty,
 * learning goals, correction cooldowns, speech quality & pronunciation evidence, and personalized tutoring context.
 */
@Singleton
class LearningMemoryManager @Inject constructor(
    private val learnerProfileRepository: LearnerProfileRepository,
    private val conceptMasteryRepository: ConceptMasteryRepository,
    private val learningEventRepository: LearningEventRepository,
    private val vocabularyMemoryRepository: VocabularyMemoryRepository,
    private val speechAnalysisRepository: SpeechAnalysisRepository,
    val masteryEngine: MasteryEngine,
    val correctionPolicyEngine: CorrectionPolicyEngine,
    val difficultyEngine: DifficultyEngine,
    val learningGoalGenerator: LearningGoalGenerator,
    val progressCalculationEngine: ProgressCalculationEngine
) {
    // Secondary constructor for standalone unit tests
    constructor(
        learnerProfileRepository: LearnerProfileRepository,
        ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.Unconfined
    ) : this(
        learnerProfileRepository = learnerProfileRepository,
        conceptMasteryRepository = object : ConceptMasteryRepository {
            private val map = ConcurrentHashMap<String, MasteryState>()
            override fun getAllMasteryFlow() = kotlinx.coroutines.flow.flowOf(map.values.toList())
            override suspend fun getAllMastery() = map.values.toList()
            override suspend fun getMastery(conceptId: String) = map[conceptId]
            override suspend fun saveMastery(mastery: MasteryState) { map[mastery.conceptId] = mastery }
            override suspend fun saveAllMastery(list: List<MasteryState>) { list.forEach { map[it.conceptId] = it } }
            override suspend fun clearAllMastery() { map.clear() }
        },
        learningEventRepository = object : LearningEventRepository {
            private val list = CopyOnWriteArrayList<LearningEvent>()
            override fun getAllEvents() = kotlinx.coroutines.flow.flowOf(list.toList())
            override suspend fun getEventsForSession(sessionId: String) = list.filter { it.sessionId == sessionId }
            override suspend fun getRecentEventsForConcept(conceptId: String) = list.filter { it.conceptId == conceptId }
            override suspend fun recordEvent(event: LearningEvent) { list.add(event) }
            override suspend fun recordEvents(events: List<LearningEvent>) { list.addAll(events) }
            override suspend fun clearAllEvents() { list.clear() }
        },
        vocabularyMemoryRepository = object : VocabularyMemoryRepository {
            private val list = CopyOnWriteArrayList<VocabularyMemory>()
            override fun getAllVocabularyMemoryFlow() = kotlinx.coroutines.flow.flowOf(list.toList())
            override suspend fun getExpressionsNeedingPractice() = list.filter { it.familiarityScore < 50 }
            override suspend fun saveExpression(memory: VocabularyMemory) { list.add(memory) }
            override suspend fun deleteExpression(id: String) { list.removeIf { it.id == id } }
            override suspend fun clearAllVocabularyMemory() { list.clear() }
        },
        speechAnalysisRepository = object : SpeechAnalysisRepository {
            private val list = CopyOnWriteArrayList<SpeechAnalysisEntity>()
            override fun getAllSpeechAnalysisFlow() = kotlinx.coroutines.flow.flowOf(list.toList())
            override suspend fun getSpeechAnalysisForSession(sessionId: String) = list.filter { it.sessionId == sessionId }
            override suspend fun getSpeechAnalysisForTurn(turnId: String) = list.find { it.turnId == turnId }
            override suspend fun recordSpeechAnalysis(entity: SpeechAnalysisEntity) { list.add(entity) }
            override suspend fun recordAllSpeechAnalysis(list: List<SpeechAnalysisEntity>) { this.list.addAll(list) }
            override suspend fun clearAllSpeechAnalysis() { list.clear() }
        },
        masteryEngine = MasteryEngine(),
        correctionPolicyEngine = CorrectionPolicyEngine(),
        difficultyEngine = DifficultyEngine(),
        learningGoalGenerator = LearningGoalGenerator(),
        progressCalculationEngine = ProgressCalculationEngine()
    ) {
        this.ioDispatcher = ioDispatcher
    }

    constructor() : this(
        object : LearnerProfileRepository {
            override suspend fun getLearnerProfile(): LearnerProfile = LearnerProfile()
            override suspend fun saveLearnerProfile(profile: LearnerProfile) {}
            override fun observeLearnerProfile() = kotlinx.coroutines.flow.emptyFlow<LearnerProfile?>()
            override suspend fun clearLearnerProfile() {}
        },
        Dispatchers.Unconfined
    )

    internal var ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.IO

    private val scope by lazy { CoroutineScope(SupervisorJob() + ioDispatcher) }

    var profile: LearnerProfile = LearnerProfile()
        private set

    val masteryMap = ConcurrentHashMap<String, MasteryState>()
    val policyState = CorrectionPolicyState()
    val activeGoals = CopyOnWriteArrayList<LearningGoal>()
    var currentDifficulty: DifficultyLevel = DifficultyLevel.INTERMEDIATE
        private set

    private val sessionLearningEvents = CopyOnWriteArrayList<LearningEvent>()
    private val sessionSpeechEvidences = CopyOnWriteArrayList<PronunciationEvidence>()

    private val initJob by lazy {
        scope.launch {
            try {
                val loadedProfile = learnerProfileRepository.getLearnerProfile()
                val loadedMastery = conceptMasteryRepository.getAllMastery()
                synchronized(this@LearningMemoryManager) {
                    profile = loadedProfile
                    loadedMastery.forEach { masteryMap[it.conceptId] = it }
                    updateAdaptiveDifficulty()
                }
            } catch (_: Exception) {}
        }
    }

    init {
        // Trigger initJob startup
        initJob
    }

    suspend fun ensureLoaded(): LearnerProfile {
        if (!initJob.isCompleted) {
            initJob.join()
        }
        return profile
    }

    suspend fun loadPersistedProfile(): LearnerProfile {
        val loaded = learnerProfileRepository.getLearnerProfile()
        val loadedMastery = conceptMasteryRepository.getAllMastery()
        synchronized(this) {
            profile = loaded
            loadedMastery.forEach { masteryMap[it.conceptId] = it }
            updateAdaptiveDifficulty()
        }
        return profile
    }

    fun setProfile(newProfile: LearnerProfile) {
        synchronized(this) {
            profile = newProfile
            updateAdaptiveDifficulty()
        }
        persistProfileAsync()
    }

    val expressionExtractor = SpokenExpressionExtractor()
    val activeVocabularyExpressions = CopyOnWriteArrayList<VocabularyMemory>()

    fun startSession(scenario: Scenario, sessionId: String) {
        sessionLearningEvents.clear()
        sessionSpeechEvidences.clear()
        policyState.consecutiveErrorsCount = 0
        policyState.consecutiveSuccessfulTurns = 0
        policyState.isStruggleBackoffActive = false
        policyState.totalSessionCorrections = 0

        // Generate dynamic session goals based on weaknesses & scenario
        val goals = learningGoalGenerator.generateGoalsForSession(profile, masteryMap.values.toList(), scenario)
        activeGoals.clear()
        activeGoals.addAll(goals)

        // Load targeted spoken expressions for the session
        activeVocabularyExpressions.clear()
        scope.launch {
            try {
                val needingPractice = vocabularyMemoryRepository.getExpressionsNeedingPractice()
                activeVocabularyExpressions.addAll(needingPractice.take(4))
            } catch (_: Exception) {}
        }

        updateAdaptiveDifficulty()
    }

    fun shouldDeliverSpokenCorrection(rawDecision: TutorCorrectionDecision): Boolean {
        if (!rawDecision.hasError || rawDecision.detectedErrors.isEmpty()) return false
        val primary = rawDecision.detectedErrors.first()
        val mastery = masteryMap[primary.ruleIdentifier]
        return correctionPolicyEngine.shouldDeliverSpokenCorrection(
            rawDecision = rawDecision,
            mastery = mastery,
            policyState = policyState,
            speakingConfidence = profile.speakingConfidenceScore
        )
    }

    fun onSpeechTurnAnalyzed(
        quality: SpeechQualityResult,
        fluency: FluencyAnalysisResult,
        pronunciation: PronunciationEvidence,
        sessionId: String,
        turnId: String
    ) {
        sessionSpeechEvidences.add(pronunciation)

        // 1. Log Speech / Fluency Learning Event if valid audio signal exists
        if (quality.isSignalUsable) {
            if (pronunciation.observedPhonemePatterns.isNotEmpty()) {
                for (sound in pronunciation.observedPhonemePatterns) {
                    val speechEvent = LearningEvent(
                        type = LearningEventType.PRONUNCIATION_OBSERVED,
                        conceptId = sound,
                        category = EnglishErrorCategory.FLUENCY_FILLER,
                        severity = CorrectionSeverity.STYLE,
                        originalUtterance = pronunciation.transcript,
                        isSuccess = true, // Reframe as practice target candidate, not a confirmed learner mispronunciation
                        sessionId = sessionId,
                        confidenceImpact = 0f
                    )
                    sessionLearningEvents.add(speechEvent)
                    persistEventAsync(speechEvent)
                }
            }

            if (fluency.hesitationType == HesitationType.LONG_HESITATION || fluency.hesitationType == HesitationType.REPEATED_HESITATION) {
                val fluencyEvent = LearningEvent(
                    type = LearningEventType.FLUENCY_OBSERVED,
                    conceptId = "hesitation_pacing",
                    category = EnglishErrorCategory.FLUENCY_FILLER,
                    severity = CorrectionSeverity.STYLE,
                    originalUtterance = pronunciation.transcript,
                    isSuccess = false,
                    sessionId = sessionId,
                    confidenceImpact = 0f
                )
                sessionLearningEvents.add(fluencyEvent)
                persistEventAsync(fluencyEvent)
            }
        }

        // 2. Persist SpeechAnalysisEntity to Room database
        val entity = SpeechAnalysisEntity(
            id = UUID.randomUUID().toString(),
            turnId = turnId,
            sessionId = sessionId,
            audioDurationMs = fluency.totalDurationMs,
            voicedDurationMs = fluency.voicedDurationMs,
            pauseCount = fluency.pauseCount,
            totalPauseDurationMs = fluency.totalPauseDurationMs,
            wordsPerMinute = fluency.wordsPerMinute,
            qualitativeFluency = fluency.qualitativeRating.name,
            qualitativePronunciation = pronunciation.qualitativeRating.name,
            hesitationType = fluency.hesitationType.name,
            snrDb = quality.snrDb,
            hasPhonemeEvidence = pronunciation.phonemeEvidenceAvailable,
            practicedSound = pronunciation.observedPhonemePatterns.firstOrNull()
        )
        scope.launch {
            try {
                speechAnalysisRepository.recordSpeechAnalysis(entity)
            } catch (_: Exception) {}
        }
    }

    fun onUtteranceAnalyzed(
        decision: TutorCorrectionDecision,
        sessionId: String? = null,
        userUtterance: String? = null
    ) {
        synchronized(this) {
            profile.totalUtterances++

            // 1. Spoken Vocabulary & Expression Extraction
            if (!userUtterance.isNullOrBlank()) {
                val extracted = expressionExtractor.extractExpressions(userUtterance)
                for (vocab in extracted) {
                    rememberVocabulary(vocab.wordOrPhrase, vocab.meaning, vocab.exampleSentence)
                    val vocabEvent = LearningEvent(
                        type = LearningEventType.VOCABULARY_LEARNED,
                        conceptId = vocab.wordOrPhrase,
                        category = EnglishErrorCategory.VOCABULARY,
                        severity = CorrectionSeverity.STYLE,
                        originalUtterance = userUtterance,
                        correctedForm = vocab.wordOrPhrase,
                        isSuccess = true,
                        sessionId = sessionId,
                        confidenceImpact = 1.0f
                    )
                    sessionLearningEvents.add(vocabEvent)
                    persistEventAsync(vocabEvent)
                }
            }

            // 2. Error Analysis vs Natural Clean Turn
            if (decision.hasError) {
                for (error in decision.detectedErrors) {
                    profile.recordMistake(error.ruleIdentifier, error.category)
                    if (profile.recentCorrections.size >= 10) {
                        profile.recentCorrections.removeAt(0)
                    }
                    profile.recentCorrections.add(error)

                    // Update mastery state with failure penalty
                    val currentMastery = masteryMap[error.ruleIdentifier]
                    val updated = masteryEngine.onMistake(error.ruleIdentifier, error.category, currentMastery)
                    masteryMap[error.ruleIdentifier] = updated
                    persistMasteryAsync(updated)

                    // Record learning event
                    val event = LearningEvent(
                        type = LearningEventType.CORRECTION,
                        conceptId = error.ruleIdentifier,
                        category = error.category,
                        severity = error.severity,
                        originalUtterance = userUtterance,
                        correctedForm = decision.correctedSentence ?: error.suggestedText,
                        isSuccess = false,
                        sessionId = sessionId,
                        confidenceImpact = -1.0f
                    )
                    sessionLearningEvents.add(event)
                    persistEventAsync(event)

                    policyState.recordError(error.ruleIdentifier)
                }

                if (decision.timing != CorrectionTiming.NO_CORRECTION) {
                    profile.correctionsDelivered++
                }
            } else {
                policyState.recordCleanTurn()
                profile.speakingConfidenceScore = (profile.speakingConfidenceScore + 0.5f).coerceAtMost(100f)

                // Natural Mastery Gain on Clean Turn for previously practiced concepts
                val weakConcept = masteryMap.values.find { it.needsPractice }
                if (weakConcept != null) {
                    val updated = masteryEngine.onNaturalCorrectUsage(weakConcept.conceptId, weakConcept.category, weakConcept)
                    masteryMap[weakConcept.conceptId] = updated
                    persistMasteryAsync(updated)

                    val masteryEvent = LearningEvent(
                        type = LearningEventType.MASTERY_GAIN,
                        conceptId = weakConcept.conceptId,
                        category = weakConcept.category,
                        severity = CorrectionSeverity.MINOR,
                        originalUtterance = userUtterance,
                        isSuccess = true,
                        sessionId = sessionId,
                        confidenceImpact = 1.0f
                    )
                    sessionLearningEvents.add(masteryEvent)
                    persistEventAsync(masteryEvent)
                }
            }

            // Refresh CEFR level estimation & difficulty
            profile.estimatedLevel = progressCalculationEngine.estimateCefrLevel(profile, masteryMap.values.toList())
            updateAdaptiveDifficulty()
        }
        persistProfileAsync()
    }

    fun onRetryEvaluated(
        evaluation: RetryEvaluation,
        sessionId: String? = null
    ) {
        synchronized(this) {
            val originalError = evaluation.originalError
            if (originalError != null) {
                val currentMastery = masteryMap[originalError.ruleIdentifier]
                if (evaluation.isFixed) {
                    profile.recordSuccessfulRetry(originalError.ruleIdentifier, originalError.category)
                    val updated = masteryEngine.onSuccessfulRetry(originalError.ruleIdentifier, originalError.category, currentMastery)
                    masteryMap[originalError.ruleIdentifier] = updated
                    persistMasteryAsync(updated)

                    val event = LearningEvent(
                        type = LearningEventType.SUCCESSFUL_RETRY,
                        conceptId = originalError.ruleIdentifier,
                        category = originalError.category,
                        severity = originalError.severity,
                        originalUtterance = evaluation.retryUtterance,
                        correctedForm = originalError.suggestedText,
                        isSuccess = true,
                        sessionId = sessionId,
                        confidenceImpact = 2.0f
                    )
                    sessionLearningEvents.add(event)
                    persistEventAsync(event)

                    // Check if any active session goal matches
                    activeGoals.find { it.targetConceptId == originalError.ruleIdentifier }?.let {
                        val idx = activeGoals.indexOf(it)
                        if (idx != -1) activeGoals[idx] = it.copy(isAchieved = true)
                    }
                } else {
                    val updated = masteryEngine.onMistake(originalError.ruleIdentifier, originalError.category, currentMastery)
                    masteryMap[originalError.ruleIdentifier] = updated
                    persistMasteryAsync(updated)

                    val event = LearningEvent(
                        type = LearningEventType.FAILED_RETRY,
                        conceptId = originalError.ruleIdentifier,
                        category = originalError.category,
                        severity = originalError.severity,
                        originalUtterance = evaluation.retryUtterance,
                        correctedForm = originalError.suggestedText,
                        isSuccess = false,
                        sessionId = sessionId,
                        confidenceImpact = -0.5f
                    )
                    sessionLearningEvents.add(event)
                    persistEventAsync(event)
                }
            }

            profile.estimatedLevel = progressCalculationEngine.estimateCefrLevel(profile, masteryMap.values.toList())
            updateAdaptiveDifficulty()
        }
        persistProfileAsync()
    }

    fun onNaturalCorrectUsage(conceptId: String, category: EnglishErrorCategory) {
        synchronized(this) {
            val current = masteryMap[conceptId]
            val updated = masteryEngine.onNaturalCorrectUsage(conceptId, category, current)
            masteryMap[conceptId] = updated
            persistMasteryAsync(updated)
        }
    }

    fun rememberVocabulary(
        wordOrPhrase: String,
        meaning: String,
        exampleSentence: String,
        sourceScenarioId: String? = null
    ) {
        val vocab = VocabularyMemory(
            wordOrPhrase = wordOrPhrase,
            meaning = meaning,
            exampleSentence = exampleSentence,
            sourceScenarioId = sourceScenarioId
        )
        scope.launch {
            try {
                vocabularyMemoryRepository.saveExpression(vocab)
            } catch (_: Exception) {}
        }
    }

    private fun updateAdaptiveDifficulty() {
        currentDifficulty = difficultyEngine.determineDifficulty(
            estimatedLevel = profile.estimatedLevel,
            speakingConfidence = profile.speakingConfidenceScore,
            successfulRetriesCount = profile.successfulRetries,
            totalUtterances = profile.totalUtterances
        )
    }

    private fun persistProfileAsync() {
        val snapshot = synchronized(this) { profile.copyProfile() }
        scope.launch {
            try {
                learnerProfileRepository.saveLearnerProfile(snapshot)
            } catch (_: Exception) {}
        }
    }

    private fun persistMasteryAsync(mastery: MasteryState) {
        scope.launch {
            try {
                conceptMasteryRepository.saveMastery(mastery)
            } catch (_: Exception) {}
        }
    }

    private fun persistEventAsync(event: LearningEvent) {
        scope.launch {
            try {
                learningEventRepository.recordEvent(event)
            } catch (_: Exception) {}
        }
    }

    fun getSessionSummary(
        sessionId: String,
        sessionDurationMs: Long,
        userTurnsCount: Int
    ): SessionLearningSummary {
        return progressCalculationEngine.generateSessionSummary(
            sessionId = sessionId,
            sessionDurationMs = sessionDurationMs,
            userTurnsCount = userTurnsCount,
            events = sessionLearningEvents.toList(),
            speakingConfidence = profile.speakingConfidenceScore,
            speechEvidences = sessionSpeechEvidences.toList()
        )
    }

    val tutorDecisionEngine: TutorDecisionEngine = TutorDecisionEngine(correctionPolicyEngine, difficultyEngine)

    fun buildLearnerState(
        isRetryActive: Boolean = false,
        retryAttemptsCount: Int = 0,
        activeRetryError: EnglishError? = null,
        sessionDurationMs: Long = 0L,
        sessionTurnCount: Int = 0,
        isFairUseExceeded: Boolean = false
    ): com.vaniflow.app.engine.learning.tutor.model.TutorLearnerState {
        val masteryScores = masteryMap.mapValues { it.value.masteryScore }
        return com.vaniflow.app.engine.learning.tutor.model.TutorLearnerState(
            cefrLevel = profile.estimatedLevel,
            speakingConfidence = profile.speakingConfidenceScore,
            confidenceTrend = if (profile.successfulRetries > 0) "Improving" else "Steady",
            weakestConcepts = profile.conceptsNeedingPractice.toList(),
            masteredConcepts = profile.masteredConcepts.toSet(),
            conceptMasteryScores = masteryScores,
            recentMistakes = profile.recentCorrections.toList(),
            successfulRetriesCount = profile.successfulRetries,
            consecutiveFailures = policyState.consecutiveErrorsCount,
            isStruggleBackoffActive = policyState.isStruggleBackoffActive,
            activeGoals = activeGoals.toList(),
            vocabularyNeedingPractice = activeVocabularyExpressions.toList(),
            latestQuality = null,
            latestFluency = null,
            latestPronunciation = sessionSpeechEvidences.lastOrNull(),
            sessionTurnCount = sessionTurnCount,
            sessionDurationMs = sessionDurationMs,
            currentDifficulty = currentDifficulty,
            isFairUseExceeded = isFairUseExceeded,
            isRetryActive = isRetryActive,
            retryAttemptsCount = retryAttemptsCount,
            activeRetryError = activeRetryError
        )
    }

    fun evaluateTutorDecision(
        state: com.vaniflow.app.engine.learning.tutor.model.TutorLearnerState,
        rawDecision: TutorCorrectionDecision? = null,
        retryEvaluation: RetryEvaluation? = null
    ): com.vaniflow.app.engine.learning.tutor.model.TutorDecision {
        return tutorDecisionEngine.evaluateDecision(state, rawDecision, retryEvaluation)
    }

    /**
     * Produces compact, bounded coaching context for injection into Cloud AI / SLM prompts
     * using the deterministic decision produced by TutorDecisionEngine.
     */
    fun getTutoringPromptContextFromDecision(decision: com.vaniflow.app.engine.learning.tutor.model.TutorDecision): String {
        val goalsText = if (activeGoals.isNotEmpty()) {
            "Active Goals: " + activeGoals.joinToString("; ") { it.title }
        } else {
            ""
        }

        val vocabText = if (!decision.suggestedVocabularyToReuse.isNullOrBlank()) {
            "Target Expression to Elicit: \"${decision.suggestedVocabularyToReuse}\""
        } else if (activeVocabularyExpressions.isNotEmpty()) {
            "Target Expressions to Model/Elicit: " + activeVocabularyExpressions.take(3).joinToString { "\"${it.wordOrPhrase}\"" }
        } else {
            ""
        }

        val speechDirective = if (sessionSpeechEvidences.isNotEmpty()) {
            val lastEv = sessionSpeechEvidences.last()
            if (lastEv.practiceSoundSuggestion != null) {
                "Speech Guidance: ${lastEv.practiceSoundSuggestion}"
            } else {
                "Speech Guidance: Encourage comfortable pacing and natural pauses."
            }
        } else {
            "Speech Guidance: Maintain natural conversational rhythm."
        }

        return """
[TUTORING CONTEXT]
${profile.getCompactSummary()}
Difficulty: ${decision.adaptiveDifficulty.displayLabel} (${decision.adaptiveDifficulty.targetSentenceComplexity})
$goalsText
$vocabText
Coaching Directive: ${decision.coachingDirective ?: "Maintain engaging conversation flow."}
$speechDirective
""".trimIndent()
    }

    /**
     * Backward-compatible helper producing prompt context.
     */
    fun getTutoringPromptContext(): String {
        val state = buildLearnerState()
        val decision = evaluateTutorDecision(state)
        return getTutoringPromptContextFromDecision(decision)
    }
}