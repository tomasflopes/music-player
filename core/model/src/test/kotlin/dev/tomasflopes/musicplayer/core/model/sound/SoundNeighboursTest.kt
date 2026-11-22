package dev.tomasflopes.musicplayer.core.model.sound

import dev.tomasflopes.musicplayer.core.model.config.IsolationGuardConfig
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

    @Test
    fun `without artists the guard changes nothing, however isolated the seed is`() {
        val space = SoundSpace(longArrayOf(1, 2, 3), 1, floatArrayOf(0f, 5f, 100f))

        val found = SoundNeighbours.nearest(space, row = 0, k = 2) { null }

        assertEquals(listOf(2L, 3L), found.map { it.songId })
    }

    @Test
    fun `an isolated seed borrows from its artist through the artistsOfSong lambda`() {
        // Songs 2..4 are a dense group, the seed (1) is far from all, and song 5 is by the seed's artist and farther.
        val ids = longArrayOf(1, 2, 3, 4, 5)
        val space = SoundSpace(ids, 1, floatArrayOf(0f, 10f, 10.1f, 10.2f, 30f))
        val artists = { id: Long -> SongArtistKeys(if (id == 1L || id == 5L) "seed" else "other") }
        val guard = IsolationGuard(IsolationGuardConfig(rank = 1, thresholdPercentile = 70.0, artistShare = 0.5))

        val found = SoundNeighbours.nearest(space, 0, 2, artists, guard) { null }

        assertEquals(listOf(2L, 5L), found.map { it.songId })
    }
}
