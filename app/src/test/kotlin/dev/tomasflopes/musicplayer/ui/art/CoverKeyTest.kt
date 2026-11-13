package dev.tomasflopes.musicplayer.ui.art

import dev.tomasflopes.musicplayer.fakes.testSong
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class CoverKeyTest {
    @Test
    fun `two songs of one album share a key`() {
        assertEquals(testSong(1, albumId = 7).coverKey(150), testSong(2, albumId = 7).coverKey(150))
    }

    @Test
    fun `songs of different albums do not`() {
        assertNotEquals(testSong(1, albumId = 7).coverKey(150), testSong(1, albumId = 8).coverKey(150))
    }

    @Test
    fun `a song without an album is keyed by itself, never by an album that has its id`() {
        assertNotEquals(testSong(5, albumId = null).coverKey(150), testSong(5, albumId = 5).coverKey(150))
        assertNotEquals(testSong(5, albumId = null).coverKey(150), testSong(6, albumId = null).coverKey(150))
    }

    @Test
    fun `the requested size is part of the key`() {
        assertNotEquals(testSong(1).coverKey(150), testSong(1).coverKey(300))
    }
}
