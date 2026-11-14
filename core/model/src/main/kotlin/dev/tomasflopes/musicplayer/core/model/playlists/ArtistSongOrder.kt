package dev.tomasflopes.musicplayer.core.model.playlists

import dev.tomasflopes.musicplayer.core.model.AlbumOrder
import dev.tomasflopes.musicplayer.core.model.Song
import dev.tomasflopes.musicplayer.core.model.SortKey

/** The order "Add whole artist" adds in: album by album, each in track order, then songs without an album. */
object ArtistSongOrder {
    fun sort(songs: List<Song>): List<Song> {
        val (withAlbum, singles) = songs.partition { it.albumId != null }
        val albums =
            withAlbum
                .groupBy { it.albumId }
                .values
                .sortedWith(compareBy({ SortKey.of(it.first().albumTitle.orEmpty()) }, { it.first().albumId?.value }))
        val lone = singles.sortedWith(compareBy({ SortKey.of(it.title) }, { it.id.value }))
        return albums.flatMap { it.sortedWith(AlbumOrder) } + lone
    }
}
