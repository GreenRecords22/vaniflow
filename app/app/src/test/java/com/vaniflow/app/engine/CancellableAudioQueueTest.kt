package com.vaniflow.app.engine

import com.vaniflow.app.engine.tts.CancellableAudioQueue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CancellableAudioQueueTest {

    @Test
    fun testAudioQueuePlaysSequentially() = runTest {
        val playedSentences = mutableListOf<String>()
        val queue = CancellableAudioQueue(this) { sentence ->
            delay(20)
            playedSentences.add(sentence)
            true
        }

        queue.enqueueSentence("Sentence one.")
        queue.enqueueSentence("Sentence two.")

        delay(100)
        assertEquals(2, playedSentences.size)
        assertEquals("Sentence one.", playedSentences[0])
        assertEquals("Sentence two.", playedSentences[1])
        assertFalse(queue.isPlaying.value)

        queue.release()
    }

    @Test
    fun testAudioQueueClearsImmediatelyOnInterruption() = runTest {
        val playedSentences = mutableListOf<String>()
        val queue = CancellableAudioQueue(this) { sentence ->
            delay(100)
            playedSentences.add(sentence)
            true
        }

        queue.enqueueSentence("Sentence one.")
        queue.enqueueSentence("Sentence two.")
        queue.enqueueSentence("Sentence three.")

        delay(20)
        // Interrupt mid-playback
        queue.clearAndStop()
        delay(150)

        // Queue should not have completed sentence two and three
        assertTrue("Interrupted queue should stop processing subsequent items", playedSentences.size <= 1)
        assertFalse(queue.isPlaying.value)

        queue.release()
    }
}
