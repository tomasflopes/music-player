package dev.tomasflopes.musicplayer.playback

import dev.tomasflopes.musicplayer.core.model.log.PlaySource
import dev.tomasflopes.musicplayer.playback.queue.refs
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class PlaybackEnginePlaylistTest {
    private val harness = EngineHarness("ABC")

    @Test
    fun `a play request from a playlist logs the playlist id`() {
        harness.engine.play(
            PlayRequest(refs("DEF"), 0, shuffle = false, PlaySource.PLAYLIST, picked = true, playlistId = 9L),
        )
        harness.startPlaying()
        harness.engine.release()

        val event = harness.history.events.last()
        assertEquals(9L, event.playlistId)
        assertEquals(PlaySource.PLAYLIST, event.source)
    }

    @Test
    fun `a play request from anywhere else logs no playlist`() {
        harness.engine.play(PlayRequest(refs("DEF"), 0, shuffle = false, PlaySource.ALBUM, picked = true))
        harness.startPlaying()
        harness.engine.release()

        assertNull(
            harness.history.events
                .last()
                .playlistId,
        )
    }
}
