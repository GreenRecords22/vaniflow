package com.vaniflow.app.engine.learning.tutor

import com.vaniflow.app.domain.model.Scenario
import com.vaniflow.app.domain.repository.ConceptMasteryRepository
import com.vaniflow.app.domain.repository.LearnerProfileRepository
import com.vaniflow.app.domain.repository.LearningEventRepository
import com.vaniflow.app.domain.repository.VocabularyMemoryRepository
import com.vaniflow.app.engine.learning.tutor.model.CorrectionPolicyState
import com.vaniflow.app.engine.learning.tutor.model.DifficultyLevel
import com.vaniflow.app.engine.learning.tutor.model.LearningEvent
import com.vaniflow.app.engine.learning.tutor.model.LearningEventType
import com.vaniflow.app.engine.learning.tutor.model.LearningGoal
import com.vaniflow.app.engine.learning.tutor.model.MasteryState
import com.vaniflow.app.engine.learning.tutor.model.SessionLearningSummary
import com.vaniflow.app.engine.learning.tutor.model.VocabularyMemory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages long-term learner intelligence, concept mastery progression, adaptive difficulty,
 * learning goals, correction cooldowns, and personalized tutoring context.
 */
@Singleton
class LearningMemoryManager @Inject constructor(
    private val learnerProfileRepository: LearnerProfileRepository,
    private val conceptMasteryRepository: ConceptMasteryRepository,
    private val learningEventRepository: LearningEventRepository,
    private val vocabularyMemoryRepository: VocabularyMemoryRepository,
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

    fun startSession(scenario: Scenario, sessionId: String) {
        sessionLearningEvents.clear()
        policyState.consecutiveErrorsCount = 0
        policyState.consecutiveSuccessfulTurns = 0
        policyState.isStruggleBackoffActive = false
        policyState.totalSessionCorrections = 0

        // Generate dynamic session goals based on weaknesses & scenario
        val goals = learningGoalGenerator.generateGoalsForSession(profile, masteryMap.values.toList(), scenario)
        activeGoals.clear()
        activeGoals.addAll(goals)

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

    fun onUtteranceAnalyzed(
        decision: TutorCorrectionDecision,
        sessionId: String? = null,
        userUtterance: String? = null
    ) {
        synchronized(this) {
            profile.totalUtterances++
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
            speakingConfidence = profile.speakingConfidenceScore
        )
    }

    /**
     * Produces compact, bounded coaching context for injection into Cloud AI / SLM prompts
     * so that the tutor naturally steers conversations toward practicing weak areas and goals.
     */
    fun getTutoringPromptContext(): String {
        val weakConcepts = profile.conceptsNeedingPractice.toList()
        val practiceSuggestion = when {
            weakConcepts.contains("tense") -> "Learner recently struggled with past tense (e.g. 'buyed' -> 'bought', 'go' -> 'went'). Naturally ask a question about yesterday or a past event."
            weakConcepts.contains("subject_verb_agreement") -> "Learner is practicing subject-verb agreement (e.g. 'he likes', 'they like'). Keep sentence examples clear."
            weakConcepts.contains("articles") -> "Learner is working on article usage ('a', 'an', 'the'). Model clean phrasing."
            weakConcepts.contains("prepositions") -> "Learner is practicing prepositions ('for 3 years', 'good at')."
            else -> "Learner is speaking smoothly. Encourage fluent storytelling and natural idioms."
        }

        val goalsText = if (activeGoals.isNotEmpty()) {
            "Active Goals: " + activeGoals.joinToString("; ") { it.title }
        } else {
            ""
        }

        return """
[TUTORING CONTEXT]
${profile.getCompactSummary()}
Difficulty: ${currentDifficulty.displayLabel} (${currentDifficulty.targetSentenceComplexity})
$goalsText
Coaching Directive: $practiceSuggestion
""".trimIndent()
    }
}