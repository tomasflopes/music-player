package dev.tomasflopes.musicplayer.core.taste.lists

import dev.tomasflopes.musicplayer.core.model.ArtistId
import dev.tomasflopes.musicplayer.core.model.Song
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ArtistPickingTest {
    private fun by(
        id: Long,
        artist: Long,
    ): Song = song(id, 0).copy(artistId = ArtistId(artist))

    private fun List<Song>.ids() = map { it.id.value }

    @Test
    fun `without preceding artists the spread is what it was`() {
        val songs = listOf(by(1, 1), by(2, 1), by(3, 2), by(4, 3))

        assertEquals(listOf(1L, 3L, 4L, 2L), songs.spreadByArtist(2).ids())
    }

    @Test
    fun `the first song keeps the gap against the artists queued before it`() {
        val songs = listOf(by(1, 5), by(2, 6), by(3, 7))

        val spread = songs.spreadByArtist(gap = 2, preceding = listOf(ArtistId(9), ArtistId(5)))

        assertEquals(listOf(2L, 3L, 1L), spread.ids())
    }

    @Test
    fun `the gap relaxes to the artist placed longest ago when everyone is inside it`() {
        val songs = listOf(by(1, 5), by(2, 6))

        val spread = songs.spreadByArtist(gap = 4, preceding = listOf(ArtistId(6), ArtistId(5)))

        assertEquals(listOf(2L, 1L), spread.ids())
    }
}
