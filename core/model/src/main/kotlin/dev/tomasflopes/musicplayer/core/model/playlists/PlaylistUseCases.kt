package dev.tomasflopes.musicplayer.core.model.playlists

import dev.tomasflopes.musicplayer.core.model.AlbumId
import dev.tomasflopes.musicplayer.core.model.AlbumOrder
import dev.tomasflopes.musicplayer.core.model.ArtistId
import dev.tomasflopes.musicplayer.core.model.SongId
import dev.tomasflopes.musicplayer.core.model.ports.SongReader
import kotlinx.coroutines.flow.first
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
    ): Result {
        val clean = PlaylistName.clean(name) ?: return Result.InvalidName
        val now = clock.millis()
        val id = store.create(clean, now)
        val added = if (firstSongs.isEmpty()) 0 else store.addSongs(id, firstSongs.distinct(), now)
        return Result.Created(id, added)
    }
}

class RenamePlaylist(
    private val store: PlaylistStore,
    private val clock: Clock,
) {
    /** `false` when [name] is not a valid name; nothing changes then. */
    suspend operator fun invoke(
        id: PlaylistId,
        name: String,
    ): Boolean {
        val clean = PlaylistName.clean(name) ?: return false
        store.rename(id, clean, clock.millis())
        return true
    }
}

/** Adding to a playlist: a song is in a playlist once, so repeats and songs already there are skipped, not doubled. */
class AddSongsToPlaylist(
    private val store: PlaylistStore,
    private val clock: Clock,
) {
    suspend operator fun invoke(
        id: PlaylistId,
        songs: List<SongId>,
    ): AddResult {
        val wanted = songs.distinct()
        if (wanted.isEmpty()) return AddResult(added = 0, alreadyThere = 0)
        val added = store.addSongs(id, wanted, clock.millis())
        return AddResult(added = added, alreadyThere = wanted.size - added)
    }
}

/** "Add whole album": every song of the album in disc and track order. */
class AddAlbumToPlaylist(
    private val songs: SongReader,
    private val add: AddSongsToPlaylist,
) {
    suspend operator fun invoke(
        id: PlaylistId,
        album: AlbumId,
    ): AddResult =
        add(
            id,
            songs
                .observeAlbumSongs(album)
                .first()
                .sortedWith(AlbumOrder)
                .map { it.id },
        )
}

/** "Add whole artist": the artist's own songs, album by album ([ArtistSongOrder]); not the featured-on songs. */
class AddArtistToPlaylist(
    private val songs: SongReader,
    private val add: AddSongsToPlaylist,
) {
    suspend operator fun invoke(
        id: PlaylistId,
        artist: ArtistId,
    ): AddResult = add(id, ArtistSongOrder.sort(songs.observeArtistSongs(artist).first()).map { it.id })
}
