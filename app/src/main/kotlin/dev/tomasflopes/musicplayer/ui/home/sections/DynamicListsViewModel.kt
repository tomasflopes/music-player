package dev.tomasflopes.musicplayer.ui.home.sections

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.tomasflopes.musicplayer.core.model.Song
import dev.tomasflopes.musicplayer.core.model.lists.DynamicListProvider
import dev.tomasflopes.musicplayer.ui.WhileSubscribed
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** What one card on Home shows: the list's name, how many songs it has now, and the first song's cover. */
data class DynamicListCard(
    val id: String,
    val title: String,
    val songCount: Int,
    val cover: Song?,
)

/** One card per registered [DynamicListProvider], in provider order; a new provider needs no change here. */
@HiltViewModel
class DynamicListsViewModel
    @Inject
    constructor(
        providers: Set<@JvmSuppressWildcards DynamicListProvider>,
    ) : ViewModel() {
        val cards: StateFlow<List<DynamicListCard>> =
            providers
                .sortedWith(compareBy({ it.order }, { it.id }))
                .map { provider -> provider.songs().map { songs -> provider.toCard(songs) } }
                .combined()
                .stateIn(viewModelScope, WhileSubscribed, emptyList())

        private fun DynamicListProvider.toCard(songs: List<Song>) =
            DynamicListCard(id, title, songs.size, songs.firstOrNull())

        /** `combine` of no flows never emits; no providers must still mean "no cards". */
        private fun List<Flow<DynamicListCard>>.combined(): Flow<List<DynamicListCard>> =
            if (isEmpty()) flowOf(emptyList()) else combine(this) { cards -> cards.toList() }
    }
