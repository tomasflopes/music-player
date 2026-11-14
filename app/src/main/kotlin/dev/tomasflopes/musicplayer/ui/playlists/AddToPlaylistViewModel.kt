package dev.tomasflopes.musicplayer.ui.playlists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.tomasflopes.musicplayer.core.model.AlbumId
import dev.tomasflopes.musicplayer.core.model.ArtistId
import dev.tomasflopes.musicplayer.core.model.SongId
import dev.tomasflopes.musicplayer.core.model.playlists.AddAlbumToPlaylist
import dev.tomasflopes.musicplayer.core.model.playlists.AddArtistToPlaylist
import dev.tomasflopes.musicplayer.core.model.playlists.AddResult
import dev.tomasflopes.musicplayer.core.model.playlists.AddSongsToPlaylist
import dev.tomasflopes.musicplayer.core.model.playlists.CreatePlaylist
import dev.tomasflopes.musicplayer.core.model.playlists.Playlist
import dev.tomasflopes.musicplayer.core.model.playlists.PlaylistId
import dev.tomasflopes.musicplayer.core.model.playlists.PlaylistName
import dev.tomasflopes.musicplayer.core.model.playlists.PlaylistReader
import dev.tomasflopes.musicplayer.ui.WhileSubscribed
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** What "add to playlist" adds: chosen songs, a whole album (track order) or a whole artist (album by album). */
sealed interface AddTarget {
    data class Songs(
        val ids: List<SongId>,
    ) : AddTarget

    data class Album(
        val id: AlbumId,
    ) : AddTarget

    data class Artist(
        val id: ArtistId,
    ) : AddTarget
}

/**
 * Behind [AddToPlaylistSheet]: the playlists, most recently used first, and the two things the sheet can do. Each
 * returns the line to show the owner, so the sheet can show it before it closes (ADR 0040).
 */
@HiltViewModel
class AddToPlaylistViewModel
    @Inject
    constructor(
        reader: PlaylistReader,
        private val create: CreatePlaylist,
        private val addSongs: AddSongsToPlaylist,
        private val addAlbum: AddAlbumToPlaylist,
        private val addArtist: AddArtistToPlaylist,
    ) : ViewModel() {
        val playlists: StateFlow<List<Playlist>> =
            reader.observePlaylists().stateIn(viewModelScope, WhileSubscribed, emptyList())

        suspend fun add(
            target: AddTarget,
            playlist: Playlist,
        ): String = AddMessage.of(run(target, playlist.id), playlist.name)

        /** `null` when [rawName] is not a valid name; nothing is created then. */
        suspend fun createAndAdd(
            target: AddTarget,
            rawName: String,
        ): String? {
            val created = create(rawName) as? CreatePlaylist.Result.Created ?: return null
            val name = PlaylistName.clean(rawName).orEmpty()
            return AddMessage.of(run(target, created.id), name)
        }

        private suspend fun run(
            target: AddTarget,
            id: PlaylistId,
        ): AddResult =
            when (target) {
                is AddTarget.Songs -> addSongs(id, target.ids)
                is AddTarget.Album -> addAlbum(id, target.id)
                is AddTarget.Artist -> addArtist(id, target.id)
            }
    }
