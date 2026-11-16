package dev.tomasflopes.musicplayer.core.model.sound

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SoundNeighboursTest {
    @Test
    fun `a file key drops folders, case and the mp3 extension`() {
        assertEquals("valete - fim da ditadura", SoundNeighbours.fileKey("HH/Valete/Valete - Fim Da Ditadura.MP3"))
        assertEquals("song", SoundNeighbours.fileKey("Song.mp3"))
        assertEquals("a.b", SoundNeighbours.fileKey("x/A.b.mp3"))
    }

    @Test
    fun `a file key does not depend on how an accent is encoded`() {
        assertEquals(SoundNeighbours.fileKey("x/Balão.mp3"), SoundNeighbours.fileKey("y/Balão.mp3"))
    }

    @Test
    fun `copies of the seed's file are left out and the next song takes their place`() {
        val space = SoundSpace(longArrayOf(1, 2, 3, 4), 1, floatArrayOf(0f, 0.1f, 2f, 3f))
        val keys = mapOf(1L to "a", 2L to "a", 3L to "b", 4L to "c")

        val found = SoundNeighbours.nearest(space, row = 0, k = 2) { keys[it] }

        assertEquals(listOf(3L, 4L), found.map { it.songId })
    }

    @Test
    fun `a seed with no known file name leaves nothing out`() {
        val space = SoundSpace(longArrayOf(1, 2), 1, floatArrayOf(0f, 1f))

        val found = SoundNeighbours.nearest(space, row = 0, k = 5) { null }

        assertEquals(listOf(2L), found.map { it.songId })
    }
}
