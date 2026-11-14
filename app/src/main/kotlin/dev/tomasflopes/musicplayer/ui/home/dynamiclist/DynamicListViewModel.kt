package dev.tomasflopes.musicplayer.ui.home.dynamiclist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.tomasflopes.musicplayer.core.model.lists.DynamicListProvider
import dev.tomasflopes.musicplayer.core.model.ports.SettingsRepository
import dev.tomasflopes.musicplayer.core.model.ref
import dev.tomasflopes.musicplayer.core.taste.lists.RecentlyAddedProvider
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
import javax.inject.Inject

/**
 * The screen of one dynamic list (design 7.7): its songs, Play and Shuffle logged with the list's own
 * [DynamicListProvider.playSource], and, for Recently added, the "Last N days" window it shows as a caption.
 *
 * The window is a Settings value and is edited only there (ADR 0022); changing it re-emits the list at once because
 * the provider reads the same value. The list is chosen by [open], called by the screen with its route argument.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DynamicListViewModel
    @Inject
    constructor(
        private val playback: PlaybackController,
        providers: Set<@JvmSuppressWildcards DynamicListProvider>,
        private val settings: SettingsRepository,
    ) : ViewModel() {
        private val providersById = providers.associateBy { it.id }
        private val listId = MutableStateFlow<String?>(null)

        val state: StateFlow<DynamicListUiState> =
            listId
                .flatMapLatest { id -> render(id) }
                .stateIn(viewModelScope, WhileSubscribed, DynamicListUiState.Loading)

        fun open(id: String) {
            listId.value = id
        }

        fun onPlay() = start(startIndex = 0, shuffle = false, picked = false)

        fun onShuffle() = start(startIndex = 0, shuffle = true, picked = false)

        /** Plays the list in its shown order, starting at the tapped song. */
        fun onSongClick(index: Int) = start(startIndex = index, shuffle = false, picked = true)

        private fun start(
            startIndex: Int,
            shuffle: Boolean,
            picked: Boolean,
        ) {
            val ready = state.value as? DynamicListUiState.Ready ?: return
            if (startIndex !in ready.songs.indices) return
            playback.play(PlayRequest(ready.songs.map { it.ref }, startIndex, shuffle, ready.playSource, picked))
        }

        private fun render(id: String?): Flow<DynamicListUiState> {
            val provider = id?.let(providersById::get)
            return when {
                id == null -> flowOf(DynamicListUiState.Loading)
                provider == null -> flowOf(DynamicListUiState.NotFound)
                else -> ready(provider)
            }
        }

        private fun ready(provider: DynamicListProvider): Flow<DynamicListUiState> =
            combine(provider.songs(), windowDays(provider)) { songs, days ->
                DynamicListUiState.Ready(provider.title, songs, provider.playSource, days)
            }

        // Only Recently added has a days window today. A second windowed list would need the provider to say so
        // (a capability on the core interface) instead of this id check.
        private fun windowDays(provider: DynamicListProvider): Flow<Int?> =
            if (provider.id == RecentlyAddedProvider.ID) settings.recentlyAddedDays else flowOf(null)
    }
