package com.vaniflow.app.engine.conversation

import com.vaniflow.app.data.local.db.dao.ConversationTurnDao
import com.vaniflow.app.data.local.db.dao.SessionDao
import com.vaniflow.app.data.local.db.entity.ConversationTurnEntity
import com.vaniflow.app.data.local.db.entity.SessionEntity
import com.vaniflow.app.domain.model.Character
import com.vaniflow.app.domain.model.ConversationState
import com.vaniflow.app.domain.model.ConversationTurn
import com.vaniflow.app.domain.model.Correction
import com.vaniflow.app.domain.model.CorrectionCategory
import com.vaniflow.app.domain.model.FeedbackImportance
import com.vaniflow.app.domain.model.Scenario
import com.vaniflow.app.domain.model.SessionScore
import com.vaniflow.app.domain.repository.DailyUsageRepository
import com.vaniflow.app.domain.repository.LearnerProfileRepository
import com.vaniflow.app.engine.ai.AIEngine
import com.vaniflow.app.engine.ai.AITurn
import com.vaniflow.app.engine.ai.analytics.DailyConversationUsageTracker
import com.vaniflow.app.engine.ai.prompt.ConversationPromptBuilder
import com.vaniflow.app.engine.character.CharacterPromptBuilder
import com.vaniflow.app.engine.learning.FeedbackEngine
import com.vaniflow.app.engine.learning.tutor.CorrectionSeverity
import com.vaniflow.app.engine.learning.tutor.EnglishCorrectionEngine
import com.vaniflow.app.engine.learning.tutor.EnglishError
import com.vaniflow.app.engine.learning.tutor.EnglishErrorCategory
import com.vaniflow.app.engine.learning.tutor.LearnerProfile
import com.vaniflow.app.engine.learning.tutor.LearningMemoryManager
import com.vaniflow.app.engine.learning.tutor.model.TutorAction
import com.vaniflow.app.engine.learning.tutor.model.TutorDecision
import com.vaniflow.app.engine.scenario.ScenarioPromptBuilder
import com.vaniflow.app.engine.speech.FluencyAnalyzer
import com.vaniflow.app.engine.speech.PronunciationAnalyzer
import com.vaniflow.app.engine.speech.SpeechFeatureExtractor
import com.vaniflow.app.engine.speech.SpeechQualityAnalyzer
import com.vaniflow.app.engine.speech.model.SpeechAudioSegment
import com.vaniflow.app.engine.tts.SentenceSplitter
import com.vaniflow.app.engine.tts.TTSEngine
import com.vaniflow.app.engine.tts.TTSResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Production ConversationEngine orchestrating natural full-duplex hands-free voice loops
 * integrated with real-time English Speaking Tutor pedagogy, persistent learner memory,
 * speech quality & pronunciation analysis, and selective retry coaching.
 */
@Singleton
class ConversationEngine private constructor(
    private val aiEngine: AIEngine,
    private val ttsEngine: TTSEngine,
    private val feedbackEngine: FeedbackEngine,
    private val correctionEngine: EnglishCorrectionEngine,
    private val learningMemoryManager: LearningMemoryManager,
    private val usageTracker: DailyConversationUsageTracker,
    val speechQualityAnalyzer: SpeechQualityAnalyzer,
    val fluencyAnalyzer: FluencyAnalyzer,
    val pronunciationAnalyzer: PronunciationAnalyzer,
    private val sessionDao: SessionDao?,
    private val conversationTurnDao: ConversationTurnDao?,
    private val learnerProfileRepository: LearnerProfileRepository?,
    private val dailyUsageRepository: DailyUsageRepository?,
    @Suppress("UNUSED_PARAMETER") marker: Unit?
) {
    @Inject
    constructor(
        aiEngine: AIEngine,
        ttsEngine: TTSEngine,
        feedbackEngine: FeedbackEngine,
        correctionEngine: EnglishCorrectionEngine,
        learningMemoryManager: LearningMemoryManager,
        usageTracker: DailyConversationUsageTracker,
        speechQualityAnalyzer: SpeechQualityAnalyzer,
        fluencyAnalyzer: FluencyAnalyzer,
        pronunciationAnalyzer: PronunciationAnalyzer,
        sessionDao: SessionDao,
        conversationTurnDao: ConversationTurnDao,
        learnerProfileRepository: LearnerProfileRepository,
        dailyUsageRepository: DailyUsageRepository
    ) : this(
        aiEngine = aiEngine,
        ttsEngine = ttsEngine,
        feedbackEngine = feedbackEngine,
        correctionEngine = correctionEngine,
        learningMemoryManager = learningMemoryManager,
        usageTracker = usageTracker,
        speechQualityAnalyzer = speechQualityAnalyzer,
        fluencyAnalyzer = fluencyAnalyzer,
        pronunciationAnalyzer = pronunciationAnalyzer,
        sessionDao = sessionDao,
        conversationTurnDao = conversationTurnDao,
        learnerProfileRepository = learnerProfileRepository,
        dailyUsageRepository = dailyUsageRepository,
        marker = null
    )

    constructor(
        aiEngine: AIEngine,
        ttsEngine: TTSEngine,
        feedbackEngine: FeedbackEngine
    ) : this(
        aiEngine = aiEngine,
        ttsEngine = ttsEngine,
        feedbackEngine = feedbackEngine,
        correctionEngine = EnglishCorrectionEngine(),
        learningMemoryManager = LearningMemoryManager(),
        usageTracker = DailyConversationUsageTracker(),
        speechQualityAnalyzer = SpeechQualityAnalyzer(SpeechFeatureExtractor()),
        fluencyAnalyzer = FluencyAnalyzer(SpeechFeatureExtractor()),
        pronunciationAnalyzer = PronunciationAnalyzer(SpeechFeatureExtractor(), SpeechQualityAnalyzer(SpeechFeatureExtractor())),
        sessionDao = null,
        conversationTurnDao = null,
        learnerProfileRepository = null,
        dailyUsageRepository = null,
        marker = null
    )

    constructor(
        aiEngine: AIEngine,
        ttsEngine: TTSEngine,
        feedbackEngine: FeedbackEngine,
        correctionEngine: EnglishCorrectionEngine,
        learningMemoryManager: LearningMemoryManager
    ) : this(
        aiEngine = aiEngine,
        ttsEngine = ttsEngine,
        feedbackEngine = feedbackEngine,
        correctionEngine = correctionEngine,
        learningMemoryManager = learningMemoryManager,
        usageTracker = DailyConversationUsageTracker(),
        speechQualityAnalyzer = SpeechQualityAnalyzer(SpeechFeatureExtractor()),
        fluencyAnalyzer = FluencyAnalyzer(SpeechFeatureExtractor()),
        pronunciationAnalyzer = PronunciationAnalyzer(SpeechFeatureExtractor(), SpeechQualityAnalyzer(SpeechFeatureExtractor())),
        sessionDao = null,
        conversationTurnDao = null,
        learnerProfileRepository = null,
        dailyUsageRepository = null,
        marker = null
    )

    enum class TutorState {
        NORMAL,
        GIVING_FEEDBACK,
        WAITING_FOR_RETRY,
        EVALUATING_RETRY
    }

    data class ActiveRetryContext(
        val originalError: EnglishError,
        val originalUtterance: String,
        val correctedSentence: String,
        var attemptsCount: Int = 1
    )

    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow(ConversationState.IDLE)
    val state: StateFlow<ConversationState> = _state.asStateFlow()

    private val _turns = MutableStateFlow<List<ConversationTurn>>(emptyList())
    val turns: StateFlow<List<ConversationTurn>> = _turns.asStateFlow()

    private val _tutorStateFlow = MutableStateFlow(TutorState.NORMAL)
    val tutorStateFlow: StateFlow<TutorState> = _tutorStateFlow.asStateFlow()

    // Friendly, user-safe error message. Never exposes raw exceptions/paths.
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private var currentSessionId: String = ""
    private var currentCharacter: Character? = null
    private var currentScenario: Scenario? = null
    private var sessionStartTimeMs: Long = 0

    private var activeGenerationJob: Job? = null
    private val isInterrupted = AtomicBoolean(false)

    var tutorState: TutorState = TutorState.NORMAL
        private set(value) {
            field = value
            _tutorStateFlow.value = value
        }

    var activeRetry: ActiveRetryContext? = null
        private set

    fun startSession(character: Character, scenario: Scenario, sessionId: String = UUID.randomUUID().toString()) {
        activeGenerationJob?.cancel()
        currentSessionId = sessionId
        currentCharacter = character
        currentScenario = scenario
        sessionStartTimeMs = System.currentTimeMillis()
        isInterrupted.set(false)
        _errorMessage.value = null
        tutorState = TutorState.NORMAL
        activeRetry = null

        // Inform the AI router which character/scenario is active (for cache scoping + resets).
        aiEngine.setActiveContext(character.id, scenario.id)
        learningMemoryManager.startSession(scenario, currentSessionId)

        // Create opening turn from scenario initial message
        val greeting = scenario.initialMessage.replace("Alex", character.name)
        val initialTurn = ConversationTurn(
            id = UUID.randomUUID().toString(),
            sessionId = currentSessionId,
            speaker = ConversationTurn.Speaker.AI,
            text = greeting,
            timestamp = System.currentTimeMillis()
        )
        _turns.value = listOf(initialTurn)
        _state.value = ConversationState.IDLE
    }

    /** Returns the stable session id persisted to Room (used by Session Summary lookup). */
    fun getCurrentSessionId(): String = currentSessionId

    fun getLearnerProfile(): LearnerProfile = learningMemoryManager.profile

    fun getActiveGoals(): List<com.vaniflow.app.engine.learning.tutor.model.LearningGoal> = learningMemoryManager.activeGoals.toList()

    fun getDifficultyLevel(): com.vaniflow.app.engine.learning.tutor.model.DifficultyLevel = learningMemoryManager.currentDifficulty

    fun getSessionSummary(): com.vaniflow.app.engine.learning.tutor.model.SessionLearningSummary {
        val durationMs = if (sessionStartTimeMs > 0) System.currentTimeMillis() - sessionStartTimeMs else 60_000L
        val userTurnsCount = _turns.value.count { it.speaker == ConversationTurn.Speaker.USER }
        return learningMemoryManager.getSessionSummary(currentSessionId, durationMs, userTurnsCount)
    }

    /**
     * Removes a trailing empty AI turn (created as a streaming placeholder) so that
     * interrupted/cancelled generations never persist blank assistant bubbles or
     * pollute the prompt history.
     */
    private fun pruneEmptyAiTurns() {
        val turns = _turns.value
        if (turns.isEmpty()) return
        val last = turns.last()
        if (last.speaker == ConversationTurn.Speaker.AI && last.text.isBlank() && last.correction == null) {
            _turns.value = turns.dropLast(1)
        }
    }

    /**
     * Cancels any in-flight generation/TTS, stops playback, and resets the
     * interruption flag. Safe to call multiple times. Used on session end and
     * ViewModel clear to guarantee no orphan coroutines update UI after teardown.
     */
    fun cancelAll() {
        isInterrupted.set(true)
        activeGenerationJob?.cancel()
        activeGenerationJob = null
        pruneEmptyAiTurns()
        engineScope.launch(Dispatchers.IO) {
            try {
                ttsEngine.stop()
            } catch (_: Exception) {
            }
        }
    }

    suspend fun playInitialGreeting() {
        val firstMessage = _turns.value.firstOrNull()?.text ?: return
        val character = currentCharacter ?: return
        _state.value = ConversationState.AI_SPEAKING
        isInterrupted.set(false)
        _errorMessage.value = null

        try {
            val result = ttsEngine.speak(firstMessage, character.voiceId, character.speakingRate)
            if (result is TTSResult.Error) {
                _errorMessage.value = "I couldn't play the greeting. Tap to retry."
                _state.value = ConversationState.IDLE
            } else if (result is TTSResult.Completed && !isInterrupted.get()) {
                // Automatically start listening for natural hands-free loop
                _state.value = ConversationState.LISTENING
            } else {
                _state.value = ConversationState.IDLE
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            _errorMessage.value = "I couldn't play the greeting. Tap to retry."
            _state.value = ConversationState.IDLE
        }
    }

    fun onUserStartedSpeaking() {
        if (_state.value == ConversationState.AI_SPEAKING || _state.value == ConversationState.THINKING) {
            _state.value = ConversationState.INTERRUPTED
            activeGenerationJob?.cancel()
            engineScope.launch(Dispatchers.IO) {
                try {
                    ttsEngine.stop()
                } catch (_: Exception) {
                }
            }
        } else {
            _state.value = ConversationState.USER_SPEAKING
        }
    }

    suspend fun processUserUtterance(userText: String, audioSegment: SpeechAudioSegment? = null) {
        if (userText.isBlank()) {
            _state.value = ConversationState.LISTENING
            return
        }

        learningMemoryManager.ensureLoaded()
        usageTracker.ensureLoaded()

        isInterrupted.set(false)
        _errorMessage.value = null
        activeGenerationJob?.cancel()
        activeGenerationJob = coroutineContext[Job]
        _state.value = ConversationState.THINKING

        val character = currentCharacter ?: return
        val scenario = currentScenario ?: return

        // -------------------------------------------------------------
        // CASE A: Active Retry Flow (Learner is repeating a correction)
        // -------------------------------------------------------------
        if (tutorState == TutorState.WAITING_FOR_RETRY && activeRetry != null) {
            val retryCtx = activeRetry!!
            val evaluation = correctionEngine.evaluateRetry(
                retryCtx.originalError,
                retryCtx.originalUtterance,
                userText
            )
            learningMemoryManager.onRetryEvaluated(evaluation, sessionId = currentSessionId)

            val userTurn = ConversationTurn(
                id = UUID.randomUUID().toString(),
                sessionId = currentSessionId,
                speaker = ConversationTurn.Speaker.USER,
                text = userText,
                timestamp = System.currentTimeMillis()
            )
            _turns.value = _turns.value + userTurn

            val learnerState = learningMemoryManager.buildLearnerState(
                isRetryActive = true,
                retryAttemptsCount = retryCtx.attemptsCount,
                activeRetryError = retryCtx.originalError,
                sessionDurationMs = if (sessionStartTimeMs > 0) System.currentTimeMillis() - sessionStartTimeMs else 0L,
                sessionTurnCount = _turns.value.count { it.speaker == ConversationTurn.Speaker.USER },
                isFairUseExceeded = usageTracker.isFairUseExceeded()
            )
            val retryDecision = learningMemoryManager.evaluateTutorDecision(
                state = learnerState,
                retryEvaluation = evaluation
            )

            if (retryDecision.action == TutorAction.PRAISE_SUCCESS) {
                tutorState = TutorState.NORMAL
                activeRetry = null
            } else if (retryDecision.action == TutorAction.GIVE_SECOND_HINT) {
                retryCtx.attemptsCount++
            } else {
                tutorState = TutorState.NORMAL
                activeRetry = null
            }

            val spokenMsg = retryDecision.spokenInterventionText ?: evaluation.praiseFeedback
            val aiTurn = ConversationTurn(
                id = UUID.randomUUID().toString(),
                sessionId = currentSessionId,
                speaker = ConversationTurn.Speaker.AI,
                text = spokenMsg,
                timestamp = System.currentTimeMillis()
            )
            _turns.value = _turns.value + aiTurn

            _state.value = ConversationState.AI_SPEAKING
            val ttsRes = ttsEngine.speak(spokenMsg, character.voiceId, character.speakingRate)
            if (!isInterrupted.get() && ttsRes !is TTSResult.Error) {
                _state.value = ConversationState.LISTENING
            } else if (ttsRes is TTSResult.Error) {
                _errorMessage.value = "I couldn't play that response. Tap to retry."
                _state.value = ConversationState.LISTENING
            }
            return
        }

        // -------------------------------------------------------------
        // CASE B: Normal Utterance Processing
        // -------------------------------------------------------------
        // 1. Analyze with EnglishCorrectionEngine & update LearningMemoryManager
        val correctionDecision = correctionEngine.analyzeUtterance(userText)
        learningMemoryManager.onUtteranceAnalyzed(correctionDecision, sessionId = currentSessionId, userUtterance = userText)

        val correction: Correction? = if (correctionDecision.hasError && correctionDecision.detectedErrors.isNotEmpty()) {
            val primary = correctionDecision.detectedErrors.first()
            Correction(
                originalText = primary.originalText,
                suggestedText = correctionDecision.correctedSentence ?: primary.suggestedText,
                explanation = primary.explanation,
                category = when (primary.category) {
                    EnglishErrorCategory.TENSE, EnglishErrorCategory.GRAMMAR, EnglishErrorCategory.SUBJECT_VERB_AGREEMENT,
                    EnglishErrorCategory.ARTICLES, EnglishErrorCategory.PREPOSITIONS, EnglishErrorCategory.SINGULAR_PLURAL -> CorrectionCategory.GRAMMAR
                    EnglishErrorCategory.NATURAL_PHRASING, EnglishErrorCategory.WORD_CHOICE, EnglishErrorCategory.WORD_ORDER,
                    EnglishErrorCategory.SENTENCE_CONSTRUCTION -> CorrectionCategory.NATURAL_PHRASING
                    EnglishErrorCategory.VOCABULARY -> CorrectionCategory.VOCABULARY
                    EnglishErrorCategory.FLUENCY_FILLER -> CorrectionCategory.FLUENCY
                },
                importance = when (primary.severity) {
                    CorrectionSeverity.CRITICAL, CorrectionSeverity.IMPORTANT -> FeedbackImportance.HIGH
                    CorrectionSeverity.MINOR -> FeedbackImportance.MEDIUM
                    CorrectionSeverity.STYLE -> FeedbackImportance.LOW
                }
            )
        } else {
            feedbackEngine.analyzeUtterance(userText)
        }

        // 2. Add user turn with attached correction
        val userTurnId = UUID.randomUUID().toString()
        val userTurn = ConversationTurn(
            id = userTurnId,
            sessionId = currentSessionId,
            speaker = ConversationTurn.Speaker.USER,
            text = userText,
            timestamp = System.currentTimeMillis(),
            correction = correction
        )
        _turns.value = _turns.value + userTurn
        pruneEmptyAiTurns()

        // 3. Speech Quality, Fluency & Pronunciation Analysis
        val segmentToAnalyze = audioSegment ?: run {
            val wordCount = userText.split("\\s+".toRegex()).count { it.isNotBlank() }
            val estMs = (wordCount * 450L).coerceIn(800L, 8000L)
            SpeechAudioSegment(samples = ShortArray((estMs * 16).toInt()), durationMs = estMs)
        }
        val quality = speechQualityAnalyzer.analyze(segmentToAnalyze)
        val fluency = fluencyAnalyzer.analyze(segmentToAnalyze, userText)
        val pronunciation = pronunciationAnalyzer.analyze(segmentToAnalyze, userText, sessionId = currentSessionId, utteranceId = userTurnId)
        learningMemoryManager.onSpeechTurnAnalyzed(quality, fluency, pronunciation, sessionId = currentSessionId, turnId = userTurnId)

        // 4. Central Pedagogical Decision by TutorDecisionEngine
        val learnerState = learningMemoryManager.buildLearnerState(
            isRetryActive = false,
            sessionDurationMs = if (sessionStartTimeMs > 0) System.currentTimeMillis() - sessionStartTimeMs else 0L,
            sessionTurnCount = _turns.value.count { it.speaker == ConversationTurn.Speaker.USER },
            isFairUseExceeded = usageTracker.isFairUseExceeded()
        )
        val tutorDecision = learningMemoryManager.evaluateTutorDecision(
            state = learnerState,
            rawDecision = correctionDecision
        )

        // 5. Intelligent Correction Decision: Check if error warrants spoken interruption + retry
        if (tutorDecision.shouldInterruptTurn && !tutorDecision.spokenInterventionText.isNullOrBlank()) {
            val spokenCorrection = tutorDecision.spokenInterventionText

            if (tutorDecision.action == TutorAction.ASK_RETRY || tutorDecision.action == TutorAction.CRITICAL_CORRECTION || tutorDecision.action == TutorAction.IMPORTANT_CORRECTION) {
                tutorState = TutorState.WAITING_FOR_RETRY
                val primaryErr = correctionDecision.detectedErrors.firstOrNull() ?: EnglishError(
                    originalText = userText,
                    suggestedText = correctionDecision.correctedSentence ?: userText,
                    category = EnglishErrorCategory.GRAMMAR,
                    severity = CorrectionSeverity.IMPORTANT,
                    explanation = spokenCorrection
                )
                activeRetry = ActiveRetryContext(
                    originalError = primaryErr,
                    originalUtterance = userText,
                    correctedSentence = correctionDecision.correctedSentence ?: primaryErr.suggestedText,
                    attemptsCount = 1
                )
            }

            val aiTurn = ConversationTurn(
                id = UUID.randomUUID().toString(),
                sessionId = currentSessionId,
                speaker = ConversationTurn.Speaker.AI,
                text = spokenCorrection,
                timestamp = System.currentTimeMillis()
            )
            _turns.value = _turns.value + aiTurn

            _state.value = ConversationState.AI_SPEAKING
            val ttsRes = ttsEngine.speak(spokenCorrection, character.voiceId, character.speakingRate)
            if (!isInterrupted.get() && ttsRes !is TTSResult.Error) {
                _state.value = ConversationState.LISTENING
            } else if (ttsRes is TTSResult.Error) {
                _errorMessage.value = "I couldn't play that response. Tap to retry."
                _state.value = ConversationState.LISTENING
            }
            return
        }

        // 6. Normal AI Conversation Response Streaming with Tutor Directive
        val history = _turns.value.map { turn ->
            AITurn(
                role = if (turn.speaker == ConversationTurn.Speaker.USER) AITurn.Role.USER else AITurn.Role.ASSISTANT,
                content = turn.text
            )
        }

        val personaPrompt = CharacterPromptBuilder.buildPersonaPrompt(character)
        val scenarioPrompt = ScenarioPromptBuilder.buildScenarioPrompt(scenario)
        val tutoringPrompt = learningMemoryManager.getTutoringPromptContextFromDecision(tutorDecision)

        val fullSystemPrompt = ConversationPromptBuilder.buildRuntimePrompt(
            characterName = character.name,
            personalityPrompt = personaPrompt,
            scenarioTitle = scenario.title,
            scenarioPrompt = scenarioPrompt,
            userLevel = character.level,
            history = history,
            userInput = userText,
            tutoringContext = tutoringPrompt
        )

        val aiTurnId = UUID.randomUUID().toString()
        val aiTurn = ConversationTurn(
            id = aiTurnId,
            sessionId = currentSessionId,
            speaker = ConversationTurn.Speaker.AI,
            text = "",
            timestamp = System.currentTimeMillis()
        )
        _turns.value = _turns.value + aiTurn

        val accumulatedText = StringBuilder()
        val sentenceBuffer = StringBuilder()

        try {
            aiEngine.streamResponse(fullSystemPrompt, history, userText)
                .catch { cause ->
                    if (cause is CancellationException) {
                        _state.value = ConversationState.INTERRUPTED
                    } else {
                        _state.value = ConversationState.ERROR
                        _errorMessage.value = "Let's try that again."
                    }
                }
                .collect { token ->
                    if (isInterrupted.get() || activeGenerationJob?.isCancelled == true) return@collect

                    accumulatedText.append(token)
                    sentenceBuffer.append(token)

                    val currentTurns = _turns.value.toMutableList()
                    val idx = currentTurns.indexOfFirst { it.id == aiTurnId }
                    if (idx != -1) {
                        val cleanedAccumulated = cleanSpokenText(accumulatedText.toString(), character.name)
                        currentTurns[idx] = currentTurns[idx].copy(text = cleanedAccumulated)
                        _turns.value = currentTurns
                    }

                    val sentences = SentenceSplitter.splitIntoSentences(sentenceBuffer.toString())
                    if (sentences.size > 1) {
                        val rawSentence = sentences.first()
                        val readySentence = cleanSpokenText(rawSentence, character.name)
                        sentenceBuffer.clear()
                        sentenceBuffer.append(sentences.drop(1).joinToString(" "))

                        if (isInterrupted.get() || activeGenerationJob?.isCancelled == true) return@collect

                        if (readySentence.isNotBlank()) {
                            _state.value = ConversationState.AI_SPEAKING
                            val ttsRes = ttsEngine.speak(readySentence, character.voiceId, character.speakingRate)
                            when (ttsRes) {
                                is TTSResult.Error -> {
                                    _errorMessage.value = "I couldn't play that response. Tap to retry."
                                    _state.value = ConversationState.LISTENING
                                    return@collect
                                }
                                is TTSResult.Interrupted -> return@collect
                                else -> Unit
                            }
                        }
                    }
                }

            val rawRemaining = sentenceBuffer.toString().trim()
            val remaining = cleanSpokenText(rawRemaining, character.name)
            if (remaining.isNotBlank() && !isInterrupted.get() && activeGenerationJob?.isCancelled != true) {
                _state.value = ConversationState.AI_SPEAKING
                val ttsRes = ttsEngine.speak(remaining, character.voiceId, character.speakingRate)
                if (ttsRes is TTSResult.Error) {
                    _errorMessage.value = "I couldn't play that response. Tap to retry."
                }
            }

            if (accumulatedText.toString().isBlank() && !isInterrupted.get()) {
                pruneEmptyAiTurns()
            }

            if (!isInterrupted.get() && activeGenerationJob?.isCancelled != true &&
                (_state.value == ConversationState.AI_SPEAKING || _state.value == ConversationState.THINKING)
            ) {
                _state.value = ConversationState.LISTENING
            }
        } catch (_: CancellationException) {
            _state.value = ConversationState.INTERRUPTED
        }
    }

    suspend fun interrupt() {
        isInterrupted.set(true)
        activeGenerationJob?.cancel()
        activeGenerationJob = null
        pruneEmptyAiTurns()
        try {
            ttsEngine.stop()
        } catch (_: Exception) {
        }
        _state.value = ConversationState.LISTENING
    }

    private fun cleanSpokenText(text: String, characterName: String): String {
        var clean = text.trim()
        val prefixes = listOf(
            "$characterName:", "$characterName :",
            "Assistant:", "Assistant :",
            "System:", "System :",
            "AI:", "AI :",
            "User:", "User :"
        )
        for (prefix in prefixes) {
            if (clean.startsWith(prefix, ignoreCase = true)) {
                clean = clean.substring(prefix.length).trim()
            }
        }
        return clean
    }

    fun endSession(): SessionScore {
        cancelAll()
        _errorMessage.value = null
        _state.value = ConversationState.SESSION_COMPLETE
        val elapsedSeconds = ((System.currentTimeMillis() - sessionStartTimeMs) / 1000L).toInt().coerceAtLeast(1)
        val elapsedMinutes = (elapsedSeconds / 60).coerceAtLeast(1)

        // Record daily speaking time in usage tracker
        usageTracker.addSpeakingDurationSeconds(elapsedSeconds)

        val summary = getSessionSummary()
        val calculatedGrammar = summary.grammarScore
        val calculatedFluency = summary.fluencyScore
        val calculatedPronunciation = if (summary.pronunciationScore > 0) summary.pronunciationScore else summary.fluencyScore
        val calculatedVocabulary = if (summary.vocabularyScore > 0) (summary.vocabularyScore * 10).coerceIn(60, 95) else ((calculatedFluency + calculatedGrammar) / 2).coerceIn(60, 95)
        val strongest = if (calculatedPronunciation >= calculatedFluency) "Pronunciation" else "Fluency"
        val focus = if (summary.conceptsNeedingPractice.isNotEmpty()) {
            summary.conceptsNeedingPractice.first().replace('_', ' ').replaceFirstChar { it.uppercaseChar() }
        } else {
            "Natural Phrasing"
        }

        val score = SessionScore(
            speakingTimeMinutes = summary.speakingMinutes,
            fluencyScore = calculatedFluency,
            grammarScore = calculatedGrammar,
            pronunciationScore = calculatedPronunciation,
            vocabularyScore = calculatedVocabulary,
            strongestArea = strongest,
            focusNext = focus,
            focusNextExplanation = "Focus on practicing $focus consistency in upcoming speaking practice."
        )

        // Persist session to Room database
        if (sessionDao != null && conversationTurnDao != null) {
            engineScope.launch(Dispatchers.IO) {
                try {
                    val sessionEntity = SessionEntity(
                        id = currentSessionId,
                        characterId = currentCharacter?.id ?: "shub",
                        scenarioId = currentScenario?.id ?: "free_conversation",
                        startTimeMs = sessionStartTimeMs,
                        endTimeMs = System.currentTimeMillis(),
                        speakingTimeMinutes = elapsedMinutes,
                        fluencyScore = calculatedFluency,
                        grammarScore = calculatedGrammar,
                        pronunciationScore = calculatedPronunciation,
                        vocabularyScore = calculatedVocabulary,
                        strongestArea = score.strongestArea,
                        focusNext = score.focusNext
                    )
                    sessionDao.insertSession(sessionEntity)

                    val turnEntities = _turns.value.map { turn ->
                        ConversationTurnEntity(
                            id = turn.id,
                            sessionId = currentSessionId,
                            speaker = turn.speaker.name,
                            text = turn.text,
                            timestamp = turn.timestamp,
                            originalMistake = turn.correction?.originalText,
                            correctionSuggestion = turn.correction?.suggestedText,
                            correctionExplanation = turn.correction?.explanation,
                            correctionCategory = turn.correction?.category?.name
                        )
                    }
                    conversationTurnDao.insertTurns(turnEntities)
                } catch (_: Exception) {}
            }
        }

        return score
    }
}
