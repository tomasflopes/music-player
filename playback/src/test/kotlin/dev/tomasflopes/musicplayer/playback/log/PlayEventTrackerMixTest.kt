package dev.tomasflopes.musicplayer.playback.log

import dev.tomasflopes.musicplayer.core.model.ArtistId
import dev.tomasflopes.musicplayer.core.model.SongId
import dev.tomasflopes.musicplayer.core.model.SongRef
import dev.tomasflopes.musicplayer.core.model.log.PlaySource
import dev.tomasflopes.musicplayer.core.model.log.StartedBy
import dev.tomasflopes.musicplayer.playback.queue.QueueEntry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class PlayEventTrackerMixTest {
    private var time = 1_000L
    private val tracker = PlayEventTracker({ time }, { time })

    private fun entry(
        manual: Boolean = false,
        mixId: Long? = null,
    ) = QueueEntry(SongRef(SongId(7), ArtistId(1)), PlaySource.MIX, manual, mixId = mixId)

    @Test
    fun `a row logs the mix its entry came from`() {
        tracker.onStarted(entry(mixId = 5), 100, StartedBy.PICKED, shuffle = false)

        val event = tracker.onStopped()

        assertEquals(5L, event?.mixId)
        assertEquals(PlaySource.MIX, event?.source)
        assertNull(event?.playlistId)
    }

    @Test
    fun `a song queued by hand is logged as queue with no mix`() {
        tracker.onStarted(entry(manual = true, mixId = 5), 100, StartedBy.AUTO, shuffle = false)

        val event = tracker.onStopped()

        assertNull(event?.mixId)
        assertEquals(PlaySource.QUEUE, event?.source)
    }

    @Test
    fun `the checkpoint carries the mix too`() {
        tracker.onStarted(entry(mixId = 5), 100, StartedBy.PICKED, shuffle = false)

        assertEquals(5L, tracker.pending()?.event?.mixId)
    }
}
