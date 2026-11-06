package com.yamahamx.editor.data.midi

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/**
 * Throttles and coalesces continuous SysEx/MIDI transmissions to prevent saturating
 * the synthesizer's USB MIDI buffer.
 *
 * Guarantees a minimum delay (e.g., 20ms) between consecutive physical MIDI writes,
 * while keeping parameter updates conflated (latest value replaces pending older values).
 */
class MidiThrottler(
    private val scope: CoroutineScope,
    private val sendIntervalMs: Long = 20L,
    private val onSend: suspend (ByteArray) -> Unit
) {
    // Map of parameter key -> latest byte payload to conflate rapid slider movements
    private val pendingUpdates = ConcurrentHashMap<String, ByteArray>()
    private val keyedSequenceJobs = ConcurrentHashMap<String, Job>()
    private val queueChannel = Channel<String>(Channel.UNLIMITED)
    private var workerJob: Job? = null
    private val mutex = Mutex()

    init {
        startWorker()
    }

    private fun startWorker() {
        workerJob?.cancel()
        workerJob = scope.launch(Dispatchers.IO) {
            for (key in queueChannel) {
                if (!isActive) break
                val payload = pendingUpdates.remove(key)
                if (payload != null) {
                    try {
                        mutex.withLock {
                            onSend(payload)
                        }
                        delay(sendIntervalMs)
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Throwable) {
                        try {
                            android.util.Log.w("MidiThrottler", "Defensive catch in throttler worker: ${e.message}", e)
                        } catch (_: Throwable) {}
                    }
                }
            }
        }
    }

    /**
     * Enqueues a message for continuous parameters with coalescing by key.
     * E.g. key = "PART_1_VOLUME"
     */
    fun enqueue(paramKey: String, payload: ByteArray) {
        pendingUpdates[paramKey] = payload
        scope.launch(Dispatchers.Default) {
            try {
                queueChannel.send(paramKey)
            } catch (_: Throwable) {}
        }
    }

    /**
     * Sends an urgent message immediately, bypassing throttling queue.
     */
    fun sendImmediate(payload: ByteArray) {
        scope.launch(Dispatchers.IO) {
            mutex.withLock {
                try {
                    onSend(payload)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    try {
                        android.util.Log.w("MidiThrottler", "Defensive catch in sendImmediate: ${e.message}", e)
                    } catch (_: Throwable) {}
                }
            }
        }
    }

    /**
     * Sends an ordered sequence of messages with a small inter-message delay (e.g. 10ms),
     * ensuring that multi-message commands like Voice changes (Bank MSB -> LSB -> Program Change)
     * are processed reliably in strict order by the synthesizer.
     */
    fun sendSequence(messages: List<ByteArray>, delayMs: Long = 10L) {
        scope.launch(Dispatchers.IO) {
            mutex.withLock {
                for (msg in messages) {
                    if (!isActive) break
                    try {
                        onSend(msg)
                        if (delayMs > 0) delay(delayMs)
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Throwable) {
                        try {
                            android.util.Log.w("MidiThrottler", "Defensive catch in sendSequence: ${e.message}", e)
                        } catch (_: Throwable) {}
                    }
                }
            }
        }
    }

    /**
     * Sends an ordered sequence of messages associated with a key (e.g., "VOICE_CHANGE_0").
     * If a previous sequence for the same key is still pending or in progress, it is cancelled
     * so rapid voice switching never floods the USB MIDI output queue.
     */
    fun sendKeyedSequence(sequenceKey: String, messages: List<ByteArray>, delayMs: Long = 10L) {
        keyedSequenceJobs.remove(sequenceKey)?.cancel()
        val job = scope.launch(Dispatchers.IO) {
            mutex.withLock {
                for (msg in messages) {
                    if (!isActive) break
                    try {
                        onSend(msg)
                        if (delayMs > 0) delay(delayMs)
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Throwable) {
                        try {
                            android.util.Log.w("MidiThrottler", "Defensive catch in sendKeyedSequence($sequenceKey): ${e.message}", e)
                        } catch (_: Throwable) {}
                    }
                }
            }
        }
        keyedSequenceJobs[sequenceKey] = job
    }

    /**
     * Clears all pending updates.
     */
    fun clear() {
        pendingUpdates.clear()
        keyedSequenceJobs.values.forEach { it.cancel() }
        keyedSequenceJobs.clear()
    }

    fun stop() {
        workerJob?.cancel()
        queueChannel.close()
        clear()
    }
}

