package com.vaniflow.app.engine.tts

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Thread-safe cancellable audio queue for sentence-level streaming playback.
 * Guarantees zero stale audio playback upon user interruption.
 */
class CancellableAudioQueue(
    private val scope: CoroutineScope,
    private val playSentenceBlock: suspend (sentence: String) -> Boolean
) {
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val queueChannel = Channel<String>(Channel.UNLIMITED)
    private var consumerJob: Job? = null
    private val isInterrupted = AtomicBoolean(false)

    init {
        startConsumer()
    }

    private fun startConsumer() {
        consumerJob?.cancel()
        consumerJob = scope.launch {
            try {
                for (sentence in queueChannel) {
                    if (isInterrupted.get() || !isActive) {
                        drainQueue()
                        break
                    }

                    _isPlaying.value = true
                    val completed = playSentenceBlock(sentence)
                    _isPlaying.value = false
                    if (!completed || isInterrupted.get()) {
                        drainQueue()
                        break
                    }
                }
            } catch (_: CancellationException) {
                drainQueue()
            } finally {
                _isPlaying.value = false
            }
        }
    }

    suspend fun enqueueSentence(sentence: String) {
        if (!isInterrupted.get() && sentence.isNotBlank()) {
            queueChannel.send(sentence)
        }
    }

    fun clearAndStop() {
        isInterrupted.set(true)
        drainQueue()
        _isPlaying.value = false
        // Restart consumer for next turn
        isInterrupted.set(false)
        startConsumer()
    }

    private fun drainQueue() {
        while (true) {
            val polled = queueChannel.tryReceive().getOrNull() ?: break
        }
    }

    fun release() {
        isInterrupted.set(true)
        consumerJob?.cancel()
        queueChannel.close()
        _isPlaying.value = false
    }
}
