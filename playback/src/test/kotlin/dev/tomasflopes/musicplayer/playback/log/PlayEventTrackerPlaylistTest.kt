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

class PlayEventTrackerPlaylistTest {
    private var time = 1_000L
    private val tracker = PlayEventTracker({ time }, { time })

    private fun entry(
        manual: Boolean = false,
        playlistId: Long? = null,
    ) = QueueEntry(SongRef(SongId(7), ArtistId(1)), PlaySource.PLAYLIST, manual, playlistId)

    @Test
    fun `a row logs the playlist its entry came from`() {
        tracker.onStarted(entry(playlistId = 5), 100, StartedBy.PICKED, shuffle = false)

        val event = tracker.onStopped()

        assertEquals(5L, event?.playlistId)
        assertEquals(PlaySource.PLAYLIST, event?.source)
    }

    @Test
    fun `a song queued by hand is logged as queue with no playlist`() {
        tracker.onStarted(entry(manual = true, playlistId = 5), 100, StartedBy.AUTO, shuffle = false)

        val event = tracker.onStopped()

        assertNull(event?.playlistId)
        assertEquals(PlaySource.QUEUE, event?.source)
    }

    @Test
    fun `the checkpoint carries the playlist too`() {
        tracker.onStarted(entry(playlistId = 5), 100, StartedBy.PICKED, shuffle = false)

        assertEquals(5L, tracker.pending()?.event?.playlistId)
    }
}
