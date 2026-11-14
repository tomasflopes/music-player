package dev.tomasflopes.musicplayer.playback.queue

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** When the engine wants the queue saved (ADR 0036). */
internal interface QueueSaving {
    /** The queue changed: save after a short pause, so a burst of changes writes once. */
    fun changed()

    /** Pause, stop or an interval while playing: save now, with the position as it is. */
    fun saveNow()

    /** The service is closing: save before returning. */
    fun saveBlocking()
}

internal object NoQueueSaving : QueueSaving {
    override fun changed() = Unit

    override fun saveNow() = Unit

    override fun saveBlocking() = Unit
}

/**
 * Saves the [queue] and the [positionMs] of the current song to the [store]. The state is captured on the caller's
 * thread (the queue is not thread safe) and written on [io], one write at a time, in order. A store that fails
 * loses that save and nothing else: playing never depends on it.
 */
internal class QueueSaver(
    private val queue: PlayQueue,
    private val store: QueueStore,
    private val positionMs: () -> Long,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
    private val debounceMs: Long,
) : QueueSaving {
    private var pending: Job? = null

    override fun changed() {
        pending?.cancel()
        pending =
            scope.launch {
                delay(debounceMs)
                write()
            }
    }

    override fun saveNow() {
        pending?.cancel()
        pending = scope.launch { write() }
    }

    override fun saveBlocking() {
        pending?.cancel()
        persist(capture())
    }

    private suspend fun write() {
        val saved = capture()
        withContext(io) { persist(saved) }
    }

    /** An empty queue is not saved: it clears what was. */
    private fun capture(): SavedQueue? =
        if (queue.size == 0) null else SavedQueue(queue.snapshot(), positionMs().coerceAtLeast(0))

    private fun persist(saved: SavedQueue?) {
        runCatching { store.save(saved) }
    }
}
