package dev.tomasflopes.musicplayer.ui.playlists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.tomasflopes.musicplayer.core.model.Song
import dev.tomasflopes.musicplayer.core.model.SongId
import dev.tomasflopes.musicplayer.core.model.log.PlaySource
import dev.tomasflopes.musicplayer.core.model.playlists.PlaylistId
import dev.tomasflopes.musicplayer.core.model.playlists.PlaylistReader
import dev.tomasflopes.musicplayer.core.model.playlists.PlaylistStore
import dev.tomasflopes.musicplayer.core.model.playlists.RenamePlaylist
import dev.tomasflopes.musicplayer.core.model.ref
import dev.tomasflopes.musicplayer.playback.PlayRequest
import dev.tomasflopes.musicplayer.playback.PlaybackController
import dev.tomasflopes.musicplayer.ui.WhileSubscribed
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface PlaylistUiState {
    data object Loading : PlaylistUiState

    /** The route named a playlist that does not exist (any more). */
    data object NotFound : PlaylistUiState

    data class Ready(
        val id: PlaylistId,
        val name: String,
        val songs: List<Song>,
    ) : PlaylistUiState
}

/**
 * One playlist: its songs in order, playing them, and editing it. The playlist is chosen by [open] from the route
 * argument. Every play carries the playlist id so the listening log can say where a song was played from (ADR 0042).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PlaylistViewModel
    @Inject
    constructor(
        private val reader: PlaylistReader,
        private val store: PlaylistStore,
        private val renamePlaylist: RenamePlaylist,
        private val playback: PlaybackController,
    ) : ViewModel() {
        private val playlistId = MutableStateFlow<PlaylistId?>(null)

        val state: StateFlow<PlaylistUiState> =
            playlistId
                .flatMapLatest { id -> if (id == null) flowOf(PlaylistUiState.Loading) else render(id) }
                .stateIn(viewModelScope, WhileSubscribed, PlaylistUiState.Loading)

        fun open(id: PlaylistId) {
            playlistId.value = id
        }

        fun onPlay() = start(0, shuffle = false, picked = false)

        fun onShuffle() = start(0, shuffle = true, picked = false)

        fun onSongClick(index: Int) = start(index, shuffle = false, picked = true)

        private fun start(
            index: Int,
            shuffle: Boolean,
            picked: Boolean,
        ) {
            val ready = state.value as? PlaylistUiState.Ready ?: return
            if (index !in ready.songs.indices) return
            val songs = ready.songs.map { it.ref }
            playback.play(PlayRequest(songs, index, shuffle, PlaySource.PLAYLIST, picked, ready.id.value))
        }

        /** `false` when [name] is not a valid name; the playlist keeps its name then. */
        suspend fun rename(name: String): Boolean {
            val id = playlistId.value ?: return false
            return renamePlaylist(id, name)
        }

        fun delete() {
            val id = playlistId.value ?: return
            viewModelScope.launch { store.delete(id) }
        }

        fun remove(song: SongId) {
            val id = playlistId.value ?: return
            viewModelScope.launch { store.removeSong(id, song) }
        }

        /** Moves the song at [from] to [to] (both positions in the list on screen); out of range is ignored. */
        fun move(
            from: Int,
            to: Int,
        ) {
            val ready = state.value as? PlaylistUiState.Ready ?: return
            if (from !in ready.songs.indices || to !in ready.songs.indices || from == to) return
            val order = ready.songs.map { it.id }.toMutableList()
            order.add(to, order.removeAt(from))
            viewModelScope.launch { store.reorder(ready.id, order) }
        }

        private fun render(id: PlaylistId): Flow<PlaylistUiState> =
            combine(reader.observePlaylist(id), reader.observeSongs(id)) { playlist, songs ->
                if (playlist == null) PlaylistUiState.NotFound else PlaylistUiState.Ready(id, playlist.name, songs)
            }
    }
