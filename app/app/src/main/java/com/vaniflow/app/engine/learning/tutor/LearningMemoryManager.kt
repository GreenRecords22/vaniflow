package com.vaniflow.app.engine.learning.tutor

import com.vaniflow.app.domain.repository.LearnerProfileRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages long-term learner progress, recurring mistakes, confidence growth, and personalized tutoring context.
 * Connects directly to LearnerProfileRepository for persistence across app process restarts.
 */
@Singleton
class LearningMemoryManager @Inject constructor(
    private val learnerProfileRepository: LearnerProfileRepository
) {
    // Test constructor
    constructor() : this(object : LearnerProfileRepository {
        override suspend fun getLearnerProfile(): LearnerProfile = LearnerProfile()
        override suspend fun saveLearnerProfile(profile: LearnerProfile) {}
        override fun observeLearnerProfile(): kotlinx.coroutines.flow.Flow<LearnerProfile?> = kotlinx.coroutines.flow.emptyFlow()
        override suspend fun clearLearnerProfile() {}
    })

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    var profile: LearnerProfile = LearnerProfile()
        private set

    init {
        learnerProfileRepository?.let { repo ->
            scope.launch {
                try {
                    val loaded = repo.getLearnerProfile()
                    profile = loaded
                } catch (_: Exception) {}
            }
        }
    }

    suspend fun loadPersistedProfile(): LearnerProfile {
        learnerProfileRepository?.let { repo ->
            val loaded = repo.getLearnerProfile()
            profile = loaded
        }
        return profile
    }

    fun setProfile(newProfile: LearnerProfile) {
        profile = newProfile
        persistProfileAsync()
    }

    fun onUtteranceAnalyzed(decision: TutorCorrectionDecision) {
        profile.totalUtterances++
        if (decision.hasError) {
            for (error in decision.detectedErrors) {
                profile.recordMistake(error.ruleIdentifier, error.category)
                if (profile.recentCorrections.size >= 10) {
                    profile.recentCorrections.removeAt(0)
                }
                profile.recentCorrections.add(error)
            }
            if (decision.timing != CorrectionTiming.NO_CORRECTION) {
                profile.correctionsDelivered++
            }
        } else {
            // Smooth natural confidence boost for clean speaking
            profile.speakingConfidenceScore = (profile.speakingConfidenceScore + 0.5f).coerceAtMost(100f)
        }
        persistProfileAsync()
    }

    fun onRetryEvaluated(evaluation: RetryEvaluation) {
        if (evaluation.isFixed && evaluation.originalError != null) {
            profile.recordSuccessfulRetry(evaluation.originalError.ruleIdentifier, evaluation.originalError.category)
        } else if (evaluation.isPartiallyFixed) {
            profile.speakingConfidenceScore = (profile.speakingConfidenceScore + 0.8f).coerceAtMost(100f)
        }
        persistProfileAsync()
    }

    private fun persistProfileAsync() {
        learnerProfileRepository?.let { repo ->
            scope.launch {
                try {
                    repo.saveLearnerProfile(profile)
                } catch (_: Exception) {}
            }
        }
    }

    /**
     * Produces compact, bounded coaching context for injection into Cloud AI prompts
     * so that the tutor naturally steers conversations toward practicing weak areas.
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

        return """
[TUTORING CONTEXT]
${profile.getCompactSummary()}
Coaching Directive: $practiceSuggestion
Tutor Persona Tone: Friendly, highly encouraging, never critical, weave conversational corrections gently.
""".trimIndent()
    }
}