package dev.tomasflopes.musicplayer.ui.library.albums

import dev.tomasflopes.musicplayer.core.model.Album
import dev.tomasflopes.musicplayer.core.model.Song
import dev.tomasflopes.musicplayer.core.model.ports.SongReader

/** An album with the song whose embedded art stands in for its cover, or `null` when none has one. */
data class AlbumListItem(
    val album: Album,
    val cover: Song?,
)

/** One query for every cover, per snapshot, instead of one per row while scrolling. */
internal suspend fun SongReader.withCovers(albums: List<Album>): List<AlbumListItem> {
    val covers = songsByIds(albums.mapNotNull { it.coverSongId }).associateBy { it.id }
    return albums.map { album -> AlbumListItem(album, album.coverSongId?.let(covers::get)) }
}
