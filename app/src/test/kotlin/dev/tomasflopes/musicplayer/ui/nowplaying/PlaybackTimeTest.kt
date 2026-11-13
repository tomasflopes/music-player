package dev.tomasflopes.musicplayer.ui.nowplaying

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PlaybackTimeTest {
    @Test
    fun `seconds are padded to two digits`() {
        assertEquals("0:05", formatPlaybackTime(5_000))
        assertEquals("3:07", formatPlaybackTime(187_000))
    }

    @Test
    fun `a partial second rounds down`() {
        assertEquals("0:00", formatPlaybackTime(999))
        assertEquals("1:00", formatPlaybackTime(60_999))
    }

    @Test
    fun `an hour or more shows hours`() {
        assertEquals("1:02:03", formatPlaybackTime(3_723_000))
    }

    @Test
    fun `a negative position shows zero`() {
        assertEquals("0:00", formatPlaybackTime(-1))
    }
}
