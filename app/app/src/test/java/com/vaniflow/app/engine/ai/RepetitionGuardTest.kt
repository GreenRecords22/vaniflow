package com.vaniflow.app.engine.ai

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for RepetitionGuard (Milestone 18, Phase 3).
 *
 * Guards against two failure modes:
 *  - letting the model repeat an earlier long answer (must be caught), and
 *  - wrongly blocking legitimate short affirmations like "Yes." / "I agree."
 */
class RepetitionGuardTest {

    @Test
    fun `records and detects a true duplicate long response`() {
        RepetitionGuard.reset()
        val a = "I really enjoy walking by the river because it helps me relax after a long day."
        RepetitionGuard.record(a)
        assertTrue(RepetitionGuard.isRepetition(a))
        assertTrue(RepetitionGuard.isRepetition(a.uppercase()))
    }

    @Test
    fun `does not block short legitimate affirmations`() {
        RepetitionGuard.reset()
        RepetitionGuard.record("I agree that daily practice is important for fluency.")
        assertFalse("'Yes.' must not be flagged", RepetitionGuard.isRepetition("Yes."))
        assertFalse("'Absolutely.' must not be flagged", RepetitionGuard.isRepetition("Absolutely."))
        assertFalse("'I agree.' must not be flagged", RepetitionGuard.isRepetition("I agree."))
    }

    @Test
    fun `different long responses are not flagged as repetition`() {
        RepetitionGuard.reset()
        RepetitionGuard.record("My favorite food is masala dosa with coconut chutney.")
        assertFalse(
            RepetitionGuard.isRepetition("I prefer hiking in the mountains on weekends.")
        )
    }

    @Test
    fun `reset clears recorded history`() {
        RepetitionGuard.reset()
        val a = "The quick brown fox jumps over the lazy dog every single morning."
        RepetitionGuard.record(a)
        RepetitionGuard.reset()
        assertFalse(RepetitionGuard.isRepetition(a))
    }

    @Test
    fun `partial overlap of long text is not a repetition`() {
        RepetitionGuard.reset()
        RepetitionGuard.record("Learning a language takes patience, practice, and a good mentor.")
        assertFalse(
            RepetitionGuard.isRepetition("Learning a language also takes consistency and curiosity.")
        )
    }
}
