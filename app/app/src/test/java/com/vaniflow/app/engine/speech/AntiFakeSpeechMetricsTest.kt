package com.vaniflow.app.engine.speech

import com.vaniflow.app.engine.learning.tutor.ProgressCalculationEngine
import com.vaniflow.app.engine.learning.tutor.model.LearningEvent
import com.vaniflow.app.engine.speech.model.PronunciationEvidence
import com.vaniflow.app.engine.speech.model.QualitativePronunciationRating
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AntiFakeSpeechMetricsTest {

    private lateinit var progressEngine: ProgressCalculationEngine

    @Before
    fun setUp() {
        progressEngine = ProgressCalculationEngine()
    }

    @Test
    fun `generateSessionSummary does not fabricate pronunciation percentage when acoustic evidence is absent`() {
        val summary = progressEngine.generateSessionSummary(
            sessionId = "test-session",
            sessionDurationMs = 60000L,
            userTurnsCount = 4,
            events = emptyList(),
            speakingConfidence = 70f,
            speechEvidences = emptyList() // No acoustic evidence
        )

        // Pronunciation score should be 0 (unmeasured), not fake 85% or 90%
        assertEquals(0, summary.pronunciationScore)
        assertEquals("Not enough evidence yet", summary.pronunciationEvidenceState)
        assertTrue(summary.pronunciationPracticeAreas.isEmpty())
    }

    @Test
    fun `generateSessionSummary reflects genuine qualitative pronunciation when evidence is available`() {
        val evidence = PronunciationEvidence(
            utteranceId = "utt-1",
            sessionId = "test-session",
            transcript = "I think about English daily",
            audioDurationMs = 2500L,
            voicedDurationMs = 1800L,
            pauseCount = 1,
            totalPauseDurationMs = 300L,
            speakingRateWpm = 120f,
            speechToSilenceRatio = 2.5f,
            signalQualityScore = 85f,
            phonemeEvidenceAvailable = true,
            observedPhonemePatterns = listOf("th_unvoiced"),
            qualitativeRating = QualitativePronunciationRating.NATURAL,
            confidence = 0.9f,
            practiceSoundSuggestion = "Place tongue between teeth"
        )

        val summary = progressEngine.generateSessionSummary(
            sessionId = "test-session",
            sessionDurationMs = 60000L,
            userTurnsCount = 4,
            events = emptyList(),
            speakingConfidence = 80f,
            speechEvidences = listOf(evidence)
        )

        assertEquals("Natural Pronunciation", summary.pronunciationEvidenceState)
        assertFalse(summary.pronunciationPracticeAreas.isEmpty())
        assertTrue(summary.pronunciationPracticeAreas.contains("Th unvoiced"))
    }
}
