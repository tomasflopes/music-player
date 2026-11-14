package dev.tomasflopes.musicplayer.ui.playlists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.tomasflopes.musicplayer.core.model.Song
import dev.tomasflopes.musicplayer.core.model.playlists.AddSongsToPlaylist
import dev.tomasflopes.musicplayer.core.model.playlists.PlaylistDismissals
import dev.tomasflopes.musicplayer.core.model.playlists.PlaylistId
import dev.tomasflopes.musicplayer.core.taste.suggest.PlaylistSuggestions
import dev.tomasflopes.musicplayer.core.taste.suggest.SuggestionPlace
import dev.tomasflopes.musicplayer.ui.WhileSubscribed
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import javax.inject.Inject

/**
 * Behind "Suggested for this playlist" (design 7.13, ADR 0051), on the playlist page and in the picker of a new
 * playlist. [open] says which playlist and where the block is shown. "+" goes through the same use case as every
 * other add, so the playlist-add taste signal is recorded (ADR 0049); "✕" dismisses the song for this playlist only.
 * Both change what the playlist and its dismissals hold, and [songs] follows, so the list refreshes at once.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PlaylistSuggestionsViewModel
    @Inject
    constructor(
        private val suggestions: PlaylistSuggestions,
        private val addSongs: AddSongsToPlaylist,
        private val dismissals: PlaylistDismissals,
        private val clock: Clock,
    ) : ViewModel() {
        private data class Target(
            val id: PlaylistId,
            val place: SuggestionPlace,
        )

        private val target = MutableStateFlow<Target?>(null)

        val songs: StateFlow<List<Song>> =
            target
                .flatMapLatest { shown ->
                    if (shown == null) flowOf(emptyList()) else suggestions.observe(shown.id, shown.place)
                }.stateIn(viewModelScope, WhileSubscribed, emptyList())

        fun open(
            id: PlaylistId,
            place: SuggestionPlace,
        ) {
            target.value = Target(id, place)
        }

        /** Adds [song] to the open playlist, at the end. */
        fun onAdd(song: Song) {
            val shown = target.value ?: return
            viewModelScope.launch { addSongs(shown.id, listOf(song.id)) }
        }

        /** Dismisses [song] for the open playlist: it does not come back there and its neighbours rank lower. */
        fun onDismiss(song: Song) {
            val shown = target.value ?: return
            viewModelScope.launch { dismissals.dismiss(shown.id, song.id, clock.millis()) }
        }
    }
