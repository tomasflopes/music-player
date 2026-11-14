package dev.tomasflopes.musicplayer.data.artistimage

import dev.tomasflopes.musicplayer.core.artistimage.ArtistCovers
import dev.tomasflopes.musicplayer.core.model.ArtistId
import dev.tomasflopes.musicplayer.core.model.Song
import dev.tomasflopes.musicplayer.core.model.ports.AlbumReader
import dev.tomasflopes.musicplayer.core.model.ports.SongReader
import kotlinx.coroutines.flow.first

/** One cover song per album of the artist, in the albums' own order, from the readers the screens use. */
class ReaderArtistCovers(
    private val albums: AlbumReader,
    private val songs: SongReader,
) : ArtistCovers {
    override suspend fun coverSongs(
        id: ArtistId,
        limit: Int,
    ): List<Song> {
        val ids =
            albums
                .observeArtistAlbums(id)
                .first()
                .mapNotNull { it.coverSongId }
                .distinct()
                .take(limit)
        val byId = songs.songsByIds(ids).associateBy { it.id }
        return ids.mapNotNull(byId::get)
    }
}
