package dev.tomasflopes.musicplayer.core.model.stats

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ListenedTimeTest {
    @Test
    fun `a normal row counts what was listened`() = assertEquals(50_000L, ListenedTime.countedMs(50_000, 200_000))

    @Test
    fun `listening past the duration is clamped to the duration`() =
        assertEquals(200_000L, ListenedTime.countedMs(900_000, 200_000))

    @Test
    fun `a missing duration counts nothing`() = assertEquals(0L, ListenedTime.countedMs(50_000, 0))

    @Test
    fun `a negative listened time counts nothing`() = assertEquals(0L, ListenedTime.countedMs(-1, 200_000))

    @Test
    fun `an impossible duration counts nothing`() =
        assertEquals(0L, ListenedTime.countedMs(50_000, ListenedTime.MAX_SONG_MS + 1))

    @Test
    fun `the longest allowed duration still counts`() =
        assertEquals(1_000L, ListenedTime.countedMs(1_000, ListenedTime.MAX_SONG_MS))
}
