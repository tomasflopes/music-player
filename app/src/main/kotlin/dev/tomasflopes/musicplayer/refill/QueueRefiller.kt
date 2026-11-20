package dev.tomasflopes.musicplayer.refill

import dev.tomasflopes.musicplayer.core.model.Song
import dev.tomasflopes.musicplayer.core.model.queue.RefillSeed
import dev.tomasflopes.musicplayer.core.model.ref
import dev.tomasflopes.musicplayer.playback.PlaybackController
import dev.tomasflopes.musicplayer.playback.RefillSignal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Keeps a seeded queue (a mix, or a single song chosen from Search) from running out (ADR 0078). It collects only the
 * small [RefillSignal] the engine publishes on queue events; when the queue is at or below its source's low-water mark
 * it builds the next batch with the [RefillSource] that handles the seed and sends `appendBatch` with the epoch the
 * signal carried, so the engine drops a batch built for a queue that is gone.
 *
 * Nothing is read until a signal with a seed arrives: [sources] is only called then. A newer signal cancels a batch
 * still being built. A seed that gave nothing is not tried again in the same epoch (a song with no sound profile).
 *
 * @param signals the engine's refill signal, `null` when the queue is not seeded or repeat is on.
 * @param sources the sources, resolved lazily (their dependencies read the library).
 */
class QueueRefiller(
    private val signals: Flow<RefillSignal?>,
    private val sources: () -> Set<RefillSource>,
    private val playback: PlaybackController,
    private val scope: CoroutineScope,
) {
    private var gaveNothing: Pair<RefillSeed, Int>? = null

    fun start() {
        scope.launch {
            signals.distinctUntilChanged().collectLatest { signal -> if (signal != null) refill(signal) }
        }
    }

    private suspend fun refill(signal: RefillSignal) {
        if (gaveNothing == signal.seed to signal.epoch) return
        val source = sources().firstOrNull { it.handles(signal.seed) } ?: return
        if (signal.nonManualAhead > source.lowWater || !source.enabled()) return
        val batch = source.batch(signal)
        if (batch.songs.isEmpty()) {
            gaveNothing = signal.seed to signal.epoch
            return
        }
        playback.appendBatch(batch.songs.map(Song::ref), signal.seed, signal.epoch, batch.source)
    }
}
