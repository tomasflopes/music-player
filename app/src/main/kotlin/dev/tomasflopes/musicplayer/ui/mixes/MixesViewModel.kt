package dev.tomasflopes.musicplayer.ui.mixes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.tomasflopes.musicplayer.core.model.Song
import dev.tomasflopes.musicplayer.core.model.SongId
import dev.tomasflopes.musicplayer.core.model.mixes.Mix
import dev.tomasflopes.musicplayer.core.model.mixes.MixId
import dev.tomasflopes.musicplayer.core.model.mixes.MixReader
import dev.tomasflopes.musicplayer.core.model.mixes.MixSeedKind
import dev.tomasflopes.musicplayer.core.model.ports.SongReader
import dev.tomasflopes.musicplayer.mix.StartMix
import dev.tomasflopes.musicplayer.ui.WhileSubscribed
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What a mix card shows: the mix and the cover of its first song seed, if it has one. */
data class MixItem(
    val mix: Mix,
    val cover: Song?,
)

/** The mixes as cards for Home, and the one call that starts one (no logic here, ADR 0076). */
@HiltViewModel
class MixesViewModel
    @Inject
    constructor(
        reader: MixReader,
        private val songs: SongReader,
        private val startMix: StartMix,
    ) : ViewModel() {
        val items: StateFlow<List<MixItem>> =
            reader
                .observeMixes()
                .map { mixes ->
                    val firstSongs =
                        mixes.mapNotNull { mix -> mix.seeds.firstOrNull { it.kind == MixSeedKind.SONG }?.refId }
                    val covers = songs.songsByIds(firstSongs.map(::SongId)).associateBy { it.id.value }
                    mixes.map { mix ->
                        val first = mix.seeds.firstOrNull { it.kind == MixSeedKind.SONG }?.refId
                        MixItem(mix, covers[first])
                    }
                }.stateIn(viewModelScope, WhileSubscribed, emptyList())

        fun start(id: MixId) {
            viewModelScope.launch { startMix(id) }
        }
    }
