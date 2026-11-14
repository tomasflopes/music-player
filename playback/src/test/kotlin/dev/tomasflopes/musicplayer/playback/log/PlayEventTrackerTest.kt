package dev.tomasflopes.musicplayer.playback.log

import dev.tomasflopes.musicplayer.core.model.ArtistId
import dev.tomasflopes.musicplayer.core.model.SongId
import dev.tomasflopes.musicplayer.core.model.SongRef
import dev.tomasflopes.musicplayer.core.model.log.EndedBy
import dev.tomasflopes.musicplayer.core.model.log.PlayEvent
import dev.tomasflopes.musicplayer.core.model.log.PlaySource
import dev.tomasflopes.musicplayer.core.model.log.StartedBy
import dev.tomasflopes.musicplayer.playback.queue.QueueEntry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PlayEventTrackerTest {
    private var time = 1_000L
    private val tracker = PlayEventTracker({ time }, { time })

    @Test
    fun `natural end is completed with full duration`() {
        tracker.onStarted(entry(7), 200_000, StartedBy.PICKED, shuffle = true)
        tracker.onPlayingChanged(true)
        time = 201_000

        assertEquals(event(7, 200_000, 200_000, EndedBy.COMPLETED, StartedBy.PICKED, true), tracker.onEndedNaturally())
    }

    @Test
    fun `paused time and seeking do not count as listening`() {
        tracker.onStarted(entry(7), 200_000, StartedBy.PICKED, shuffle = false)
        tracker.onPlayingChanged(true)
        time += 10_000
        tracker.onPlayingChanged(false)
        time += 600_000
        tracker.onPlayingChanged(true)
        time += 5_000

        assertEquals(
            event(7, 15_000, 200_000, EndedBy.SKIPPED, StartedBy.PICKED, false),
            tracker.onStarted(entry(8), 200_000, StartedBy.NAV, false),
        )
    }

    @Test
    fun `manual queued item is logged with queue source`() {
        tracker.onStarted(entry(7, manual = true), 100, StartedBy.AUTO, shuffle = false)
        tracker.onPlayingChanged(true)
        time += 100

        assertEquals(
            event(7, 100, 100, EndedBy.COMPLETED, StartedBy.AUTO, false, PlaySource.QUEUE),
            tracker.onEndedNaturally(),
        )
    }

    @Test
    fun `a song started while the player keeps playing counts time from the start`() {
        tracker.onStarted(entry(7), 200_000, StartedBy.PICKED, shuffle = false)
        tracker.onPlayingChanged(true)
        time += 5_000
        val skipped = tracker.onStarted(entry(8), 200_000, StartedBy.NAV, shuffle = false, playing = true)
        time += 7_000

        assertEquals(5_000L, skipped?.listenedMs)
        assertEquals(7_000L, tracker.onStarted(entry(9), 200_000, StartedBy.NAV, shuffle = false)?.listenedMs)
    }

    @Test
    fun `the pending checkpoint does not stop the listening clock`() {
        tracker.onStarted(entry(7), 200_000, StartedBy.PICKED, shuffle = false)
        tracker.onPlayingChanged(true)
        time += 5_000
        assertEquals(5_000L, tracker.pending()?.listenedMs)
        time += 5_000

        assertEquals(10_000L, tracker.pending()?.listenedMs)
        assertEquals(10_000L, tracker.onStarted(entry(8), 200_000, StartedBy.NAV, shuffle = false)?.listenedMs)
    }

    private fun entry(
        id: Long,
        manual: Boolean = false,
    ) = QueueEntry(SongRef(SongId(id), ArtistId(1)), PlaySource.SHUFFLE_ALL, manual)

    private fun event(
        id: Long,
        listened: Long,
        duration: Long,
        endedBy: EndedBy,
        startedBy: StartedBy,
        shuffle: Boolean,
        source: PlaySource = PlaySource.SHUFFLE_ALL,
    ) = PlayEvent(SongId(id), 1_000, listened, duration, endedBy, source, startedBy, shuffle)
}
