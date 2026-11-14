package dev.tomasflopes.musicplayer.core.model.playlists

import dev.tomasflopes.musicplayer.core.model.AlbumId
import dev.tomasflopes.musicplayer.core.model.Song
import dev.tomasflopes.musicplayer.core.model.song
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ArtistSongOrderTest {
    private fun onAlbum(
        name: String,
        album: Long?,
        albumTitle: String?,
        track: Int? = null,
        id: Long,
    ): Song = song(name, track = track, id = id).copy(albumId = album?.let(::AlbumId), albumTitle = albumTitle)

    @Test
    fun `albums go one after another by title, each in track order, and singles come last`() {
        val b2 = onAlbum("b2", 2, "Beta", track = 2, id = 1)
        val b1 = onAlbum("b1", 2, "Beta", track = 1, id = 2)
        val a1 = onAlbum("a1", 1, "alpha", track = 1, id = 3)
        val loneZ = onAlbum("Zed", null, null, id = 4)
        val loneY = onAlbum("yak", null, null, id = 5)

        val sorted = ArtistSongOrder.sort(listOf(b2, loneZ, a1, loneY, b1))

        assertEquals(listOf(a1, b1, b2, loneY, loneZ), sorted)
    }

    @Test
    fun `an artist with no songs gives an empty list`() =
        assertEquals(emptyList<Song>(), ArtistSongOrder.sort(emptyList()))
}
