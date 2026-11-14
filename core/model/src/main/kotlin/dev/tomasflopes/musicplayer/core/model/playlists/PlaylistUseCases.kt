package dev.tomasflopes.musicplayer.core.model.playlists

import dev.tomasflopes.musicplayer.core.model.AlbumId
import dev.tomasflopes.musicplayer.core.model.ArtistId
import dev.tomasflopes.musicplayer.core.model.SongId
import dev.tomasflopes.musicplayer.core.model.ports.SongReader
import java.time.Clock

/** What adding songs did: [added] went in, [alreadyThere] were in the playlist already and were left alone. */
data class AddResult(
    val added: Int,
    val alreadyThere: Int,
)

/** A new playlist, optionally with its first songs. */
class CreatePlaylist(
    private val store: PlaylistStore,
    private val clock: Clock,
) {
    sealed interface Result {
        data class Created(
            val id: PlaylistId,
            val added: Int,
        ) : Result

        data object InvalidName : Result
    }

    suspend operator fun invoke(
        name: String,
        firstSongs: List<SongId> = emptyList(),
    ): Result = TODO("not yet")
}

class RenamePlaylist(
    private val store: PlaylistStore,
    private val clock: Clock,
) {
    /** `false` when [name] is not a valid name; nothing changes then. */
    suspend operator fun invoke(
        id: PlaylistId,
        name: String,
    ): Boolean = TODO("not yet")
}

/** Adding to a playlist: a song is in a playlist once, so repeats and songs already there are skipped, not doubled. */
class AddSongsToPlaylist(
    private val store: PlaylistStore,
    private val clock: Clock,
) {
    suspend operator fun invoke(
        id: PlaylistId,
        songs: List<SongId>,
    ): AddResult = TODO("not yet")
}

/** "Add whole album": every song of the album in disc and track order. */
class AddAlbumToPlaylist(
    private val songs: SongReader,
    private val add: AddSongsToPlaylist,
) {
    suspend operator fun invoke(
        id: PlaylistId,
        album: AlbumId,
    ): AddResult = TODO("not yet")
}

/** "Add whole artist": the artist's own songs, album by album ([ArtistSongOrder]); featured-on songs are not included. */
class AddArtistToPlaylist(
    private val songs: SongReader,
    private val add: AddSongsToPlaylist,
) {
    suspend operator fun invoke(
        id: PlaylistId,
        artist: ArtistId,
    ): AddResult = TODO("not yet")
}
