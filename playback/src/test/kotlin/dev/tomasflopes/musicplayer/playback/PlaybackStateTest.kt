package dev.tomasflopes.musicplayer.playback

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlaybackStateTest {
    private val state = PlaybackState(upcoming = listOf(song(1), song(2), song(3)), manualUpcoming = 2)

    @Test
    fun `the first manualUpcoming upcoming songs are manual`() {
        assertTrue(state.isManualUpcoming(0))
        assertTrue(state.isManualUpcoming(1))
    }

    @Test
    fun `later upcoming songs and out of range indexes are not manual`() {
        assertFalse(state.isManualUpcoming(2))
        assertFalse(state.isManualUpcoming(-1))
    }
}
