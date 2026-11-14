package dev.tomasflopes.musicplayer.ui.playlists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.tomasflopes.musicplayer.core.model.Song
import dev.tomasflopes.musicplayer.core.model.playlists.CreatePlaylist
import dev.tomasflopes.musicplayer.core.model.playlists.Playlist
import dev.tomasflopes.musicplayer.core.model.playlists.PlaylistId
import dev.tomasflopes.musicplayer.core.model.playlists.PlaylistReader
import dev.tomasflopes.musicplayer.core.model.ports.SongReader
import dev.tomasflopes.musicplayer.ui.WhileSubscribed
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** What a playlist card or row shows: the playlist and the cover of its first song. */
data class PlaylistItem(
    val playlist: Playlist,
    val cover: Song?,
)

/** The playlists as cards and rows, most recently used first: one source for Home and the Library tab. */
@HiltViewModel
class PlaylistsViewModel
    @Inject
    constructor(
        reader: PlaylistReader,
        private val songs: SongReader,
        private val createPlaylist: CreatePlaylist,
    ) : ViewModel() {
        val items: StateFlow<List<PlaylistItem>> =
            reader
                .observePlaylists()
                .map { playlists ->
                    val covers = songs.songsByIds(playlists.mapNotNull { it.coverSongId }).associateBy { it.id }
                    playlists.map { PlaylistItem(it, covers[it.coverSongId]) }
                }.stateIn(viewModelScope, WhileSubscribed, emptyList())

        /** The new playlist's id, or `null` when [rawName] is not a valid name. */
        suspend fun create(rawName: String): PlaylistId? =
            (createPlaylist(rawName) as? CreatePlaylist.Result.Created)?.id
    }
