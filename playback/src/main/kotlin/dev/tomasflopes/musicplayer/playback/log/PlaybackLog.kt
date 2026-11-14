package dev.tomasflopes.musicplayer.playback.log

import dev.tomasflopes.musicplayer.core.model.SongId
import dev.tomasflopes.musicplayer.core.model.log.StartedBy
import dev.tomasflopes.musicplayer.playback.queue.PlayQueue

/**
 * Turns "the player moved" into listening-log rows (ADR 0025). The queue says which entry is current and whether
 * shuffle is on; the library says how long the song is, because the player does not know until it has opened the
 * file. Every path that changes the current song calls [started], so no track reaches the player unlogged.
 */
internal class PlaybackLog(
    private val recorder: PlayEventRecorder,
    private val queue: PlayQueue,
    private val durationOf: (SongId) -> Long,
    private val isPlaying: () -> Boolean,
) {
    /** Set after a queue was restored: the first time the player plays, a row starts for its current song. */
    private var awaitingFirstPlay = false

    /** The queue's current song has just become the one playing; the one before it is closed as skipped. */
    fun started(startedBy: StartedBy) {
        awaitingFirstPlay = false
        val entry = queue.current ?: return
        recorder.started(entry, durationOf(entry.ref.songId), startedBy, queue.shuffled, isPlaying())
    }

    /** The current song played to its end. */
    fun completed() = recorder.endedNaturally()

    /** Playback of the current song ended without a skip: the file would not open, or the service is closing. */
    fun stopped() = recorder.stopped()

    /** Previous restarted the current song: the listening so far is a stopped row, the replay a new nav row. */
    fun restarted() {
        recorder.stopped()
        started(StartedBy.NAV)
    }

    /** A restored queue is parked, paused, with no row; the owner's first play begins it (ADR 0036). */
    fun awaitFirstPlay() {
        awaitingFirstPlay = true
    }

    fun playingChanged(playing: Boolean) {
        // A resumed song is not a pick: it starts as auto, with the clock running from this play.
        if (playing && awaitingFirstPlay) started(StartedBy.AUTO)
        recorder.playingChanged(playing)
    }
}
