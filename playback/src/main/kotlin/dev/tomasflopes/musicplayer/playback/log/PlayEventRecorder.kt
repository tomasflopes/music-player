package dev.tomasflopes.musicplayer.playback.log

import dev.tomasflopes.musicplayer.core.model.log.PlayEvent
import dev.tomasflopes.musicplayer.core.model.log.StartedBy
import dev.tomasflopes.musicplayer.core.model.ports.PlayHistoryWriter
import dev.tomasflopes.musicplayer.playback.queue.QueueEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Bridges player lifecycle callbacks to the append-only listening history. */
class PlayEventRecorder(
    private val history: PlayHistoryWriter,
    private val scope: CoroutineScope,
    private val pending: PendingPlayStore? = null,
    private val tracker: PlayEventTracker = PlayEventTracker(System::currentTimeMillis),
) {
    init {
        append(pending?.takeLeftover())
    }

    fun started(
        entry: QueueEntry,
        durationMs: Long,
        startedBy: StartedBy,
        shuffle: Boolean,
        playing: Boolean = false,
    ) {
        append(tracker.onStarted(entry, durationMs, startedBy, shuffle, playing))
        pending?.save(tracker.pending())
    }

    fun playingChanged(isPlaying: Boolean) {
        tracker.onPlayingChanged(isPlaying)
        pending?.save(tracker.pending())
    }

    fun endedNaturally() = finish(tracker.onEndedNaturally())

    fun stopped() = finish(tracker.onStopped())

    private fun finish(event: PlayEvent?) {
        pending?.save(null)
        append(event)
    }

    private fun append(event: PlayEvent?) {
        event ?: return
        scope.launch { history.append(event) }
    }
}
