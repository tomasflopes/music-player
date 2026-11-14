package dev.tomasflopes.musicplayer.playback.log

import dev.tomasflopes.musicplayer.core.model.log.EndedBy
import dev.tomasflopes.musicplayer.core.model.log.PlayEvent
import dev.tomasflopes.musicplayer.core.model.log.PlaySource
import dev.tomasflopes.musicplayer.core.model.log.StartedBy
import dev.tomasflopes.musicplayer.playback.queue.QueueEntry

/** Milliseconds on a clock that only moves forward; the default listening clock (ADR 0031). */
fun monotonicMs(): Long = System.nanoTime() / NANOS_PER_MS

private const val NANOS_PER_MS = 1_000_000L

/**
 * Pure state machine for one audible item at a time. It deliberately measures time spent playing rather
 * than player position, so seeking and time spent paused never distort the listening log.
 *
 * Two clocks, both injected (ADR 0031): [wallMs] stamps when a row started, because that is a date; [monoMs] times
 * how long it played, because a wall clock that is set or corrected mid-song would otherwise bend the listened time.
 */
class PlayEventTracker(
    private val wallMs: () -> Long,
    private val monoMs: () -> Long,
) {
    private var active: Active? = null

    /**
     * Starts [entry], returning the interrupted item as a skip when there was one. Pass [playing] when the player is
     * already playing: gapless and queue moves fire no "is playing" change, so the clock has to start here.
     */
    fun onStarted(
        entry: QueueEntry,
        durationMs: Long,
        startedBy: StartedBy,
        shuffle: Boolean,
        playing: Boolean = false,
    ): PlayEvent? {
        val finished = finish(EndedBy.SKIPPED)
        val now = monoMs()
        active =
            Active(
                entry = entry,
                startedAtMs = wallMs(),
                durationMs = durationMs.coerceAtLeast(0),
                startedBy = startedBy,
                shuffle = shuffle,
                playingSinceMs = if (playing) now else null,
            )
        return finished
    }

    fun onPlayingChanged(isPlaying: Boolean) {
        val current = active ?: return
        val now = monoMs()
        if (isPlaying && current.playingSinceMs == null) current.playingSinceMs = now
        if (!isPlaying) current.stopListening(now)
    }

    fun onEndedNaturally(): PlayEvent? = finish(EndedBy.COMPLETED, completed = true)

    fun onStopped(): PlayEvent? = finish(EndedBy.STOPPED)

    /** The event to append as stopped after an unexpected process death. */
    fun pending(): PlayEvent? = active?.event(EndedBy.STOPPED, monoMs())

    private fun finish(
        endedBy: EndedBy,
        completed: Boolean = false,
    ): PlayEvent? {
        val current = active ?: return null
        active = null
        return current.event(endedBy, monoMs(), completed)
    }

    private class Active(
        val entry: QueueEntry,
        val startedAtMs: Long,
        val durationMs: Long,
        val startedBy: StartedBy,
        val shuffle: Boolean,
        var listenedMs: Long = 0,
        var playingSinceMs: Long? = null,
    ) {
        fun stopListening(now: Long) {
            val since = playingSinceMs ?: return
            listenedMs += (now - since).coerceAtLeast(0)
            playingSinceMs = null
        }

        /** Time listened up to [now], without touching the clock: a checkpoint must not stop it. */
        private fun listenedAt(now: Long): Long {
            val running = playingSinceMs?.let { (now - it).coerceAtLeast(0) } ?: 0
            return listenedMs + running
        }

        fun event(
            endedBy: EndedBy,
            now: Long,
            completed: Boolean = false,
        ): PlayEvent =
            PlayEvent(
                songId = entry.ref.songId,
                startedAtEpochMs = startedAtMs,
                listenedMs = if (completed) durationMs else listenedAt(now),
                songDurationMs = durationMs,
                endedBy = endedBy,
                source = if (entry.manual) PlaySource.QUEUE else entry.source,
                startedBy = startedBy,
                shuffle = shuffle,
            )
    }
}
