package com.vaniflow.app.engine.learning.tutor

import com.vaniflow.app.engine.learning.tutor.model.MasteryState
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class MasteryEngineTest {

    private lateinit var engine: MasteryEngine

    @Before
    fun setup() {
        engine = MasteryEngine()
    }

    @Test
    fun `mistake decreases mastery score and increases practice priority`() {
        val initial = MasteryState(
            conceptId = "past_buyed",
            category = EnglishErrorCategory.TENSE,
            masteryScore = 50,
            attemptCount = 2,
            successCount = 1,
            failureCount = 1
        )

        val updated = engine.onMistake("past_buyed", EnglishErrorCategory.TENSE, initial)
        assertEquals(38, updated.masteryScore) // 50 - 12
        assertEquals(3, updated.attemptCount)
        assertEquals(2, updated.failureCount)
        assertEquals(1, updated.consecutiveFailures)
        assertEquals(0, updated.consecutiveSuccesses)
        assertTrue(updated.practicePriority > 50)
    }

    @Test
    fun `successful retry boosts mastery score and resets consecutive failures`() {
        val initial = MasteryState(
            conceptId = "past_buyed",
            category = EnglishErrorCategory.TENSE,
            masteryScore = 38,
            attemptCount = 3,
            successCount = 1,
            failureCount = 2,
            consecutiveFailures = 1
        )

        val updated = engine.onSuccessfulRetry("past_buyed", EnglishErrorCategory.TENSE, initial)
        assertEquals(53, updated.masteryScore) // 38 + 15
        assertEquals(4, updated.attemptCount)
        assertEquals(2, updated.successCount)
        assertEquals(1, updated.consecutiveSuccesses)
        assertEquals(0, updated.consecutiveFailures)
    }

    @Test
    fun `repeated successes reach mastered threshold`() {
        var state: MasteryState? = null
        for (i in 1..5) {
            state = engine.onSuccessfulRetry("past_buyed", EnglishErrorCategory.TENSE, state)
        }

        assertNotNull(state)
        assertTrue("Score must be high after 5 retries", state!!.masteryScore >= 80)
        assertTrue("Concept must be considered mastered", state.isMastered)
        assertFalse("Concept should not need urgent practice", state.needsPractice)
    }

    @Test
    fun `time decay slowly reduces score after grace period`() {
        val now = System.currentTimeMillis()
        val fifteenDaysAgo = now - (15L * 24 * 60 * 60 * 1000)

        val mastered = MasteryState(
            conceptId = "past_buyed",
            category = EnglishErrorCategory.TENSE,
            masteryScore = 90,
            consecutiveSuccesses = 4,
            lastPracticedEpochMs = fifteenDaysAgo
        )

        val decayed = engine.applyTimeDecay(mastered, now)
        assertTrue("Score must have decayed from 90", decayed.masteryScore < 90)
        assertTrue("Score must not drop below floor", decayed.masteryScore >= 10)
    }

    @Test
    fun `zero attempts default initial state is handled cleanly`() {
        val created = engine.onMistake("sva_he_dont", EnglishErrorCategory.SUBJECT_VERB_AGREEMENT, null)
        assertEquals("sva_he_dont", created.conceptId)
        assertEquals(8, created.masteryScore) // 20 - 12
        assertEquals(1, created.attemptCount)
        assertEquals(1, created.failureCount)
    }
}
