package dev.tomasflopes.musicplayer.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.tomasflopes.musicplayer.core.model.log.PlaySource
import dev.tomasflopes.musicplayer.core.model.ports.LibraryScanController
import dev.tomasflopes.musicplayer.core.model.ports.ScanProgress
import dev.tomasflopes.musicplayer.core.model.ports.SongReader
import dev.tomasflopes.musicplayer.playback.PlayRequest
import dev.tomasflopes.musicplayer.playback.PlaybackController
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The Home tab: which sections it shows and in what order, the library's scan state, and Shuffle all
 * (which the Shuffle all section draws and calls through this ViewModel).
 */
@HiltViewModel
class HomeViewModel
    @Inject
    constructor(
        private val playback: PlaybackController,
        private val songs: SongReader,
        private val scanner: LibraryScanController,
        homeSections: Set<@JvmSuppressWildcards HomeSection>,
    ) : ViewModel() {
        init {
            val ids = homeSections.map { it.id }
            require(ids.size == ids.toSet().size) { "Home section ids must be unique, got $ids" }
        }

        val sections: List<HomeSection> = homeSections.sortedWith(compareBy({ it.order }, { it.id }))

        val scan: StateFlow<ScanProgress> = scanner.progress

        fun rescan() = scanner.requestScan()

        /** Every song once, in a random order; does nothing while the library is empty. */
        fun shuffleAll() {
            viewModelScope.launch {
                val refs = songs.allSongRefs()
                if (refs.isEmpty()) return@launch
                playback.play(PlayRequest(refs, 0, shuffle = true, PlaySource.SHUFFLE_ALL, picked = false))
            }
        }
    }
