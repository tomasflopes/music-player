package dev.tomasflopes.musicplayer.core.model.ports

import dev.tomasflopes.musicplayer.core.model.Album
import dev.tomasflopes.musicplayer.core.model.AlbumId
import dev.tomasflopes.musicplayer.core.model.Artist
import dev.tomasflopes.musicplayer.core.model.ArtistId
import dev.tomasflopes.musicplayer.core.model.Folder
import dev.tomasflopes.musicplayer.core.model.Song
import dev.tomasflopes.musicplayer.core.model.SongId
import dev.tomasflopes.musicplayer.core.model.SongRef
import kotlinx.coroutines.flow.Flow

/** Songs in the library (only songs inside the music folder). Implemented by `:data`. */
interface SongReader {
    /** By sort title. */
    fun observeSongs(): Flow<List<Song>>

    /** Newest first. */
    fun observeAddedSince(epochSec: Long): Flow<List<Song>>

    /** In [dev.tomasflopes.musicplayer.core.model.AlbumOrder]. */
    fun observeAlbumSongs(id: AlbumId): Flow<List<Song>>

    fun observeArtistSongs(id: ArtistId): Flow<List<Song>>

    /** Songs by other artists whose title features this one. */
    fun observeFeaturedOn(id: ArtistId): Flow<List<Song>>

    /** Direct children only, in [dev.tomasflopes.musicplayer.core.model.NaturalOrder] of file name. */
    fun observeFolderSongs(folderPath: String): Flow<List<Song>>

    /** Same order as [ids]; ids no longer in the library are left out. */
    suspend fun songsByIds(ids: List<SongId>): List<Song>

    suspend fun allSongRefs(): List<SongRef>
}

interface ArtistReader {
    fun observeArtists(): Flow<List<Artist>>

    fun observeArtist(id: ArtistId): Flow<Artist?>
}

interface AlbumReader {
    fun observeAlbums(): Flow<List<Album>>

    fun observeAlbum(id: AlbumId): Flow<Album?>

    fun observeArtistAlbums(id: ArtistId): Flow<List<Album>>
}

interface FolderReader {
    /** [parentPath] `null` means the music folder itself. */
    fun observeChildren(parentPath: String?): Flow<List<Folder>>
}
