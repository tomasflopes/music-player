package dev.tomasflopes.musicplayer.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.tomasflopes.musicplayer.core.model.Song
import dev.tomasflopes.musicplayer.core.model.log.PlaySource
import dev.tomasflopes.musicplayer.core.model.ports.SongReader
import dev.tomasflopes.musicplayer.core.model.ref
import dev.tomasflopes.musicplayer.playback.PlayRequest
import dev.tomasflopes.musicplayer.playback.PlaybackController
import dev.tomasflopes.musicplayer.ui.WhileSubscribed
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** The Library tab. Today it lists every song; Artists, Albums and Folders join it as tabs of their own. */
@HiltViewModel
class LibraryViewModel
    @Inject
    constructor(
        private val playback: PlaybackController,
        songReader: SongReader,
    ) : ViewModel() {
        /** `null` until the first read, so an empty library and a loading one look different. */
        val songs: StateFlow<List<Song>?> =
            songReader.observeSongs().stateIn(viewModelScope, WhileSubscribed, null)

        /** Plays the list in its shown order, starting at the tapped song. */
        fun playFrom(index: Int) {
            val list = songs.value ?: return
            if (index !in list.indices) return
            playback.play(PlayRequest(list.map { it.ref }, index, shuffle = false, PlaySource.SONGS, picked = true))
        }
    }
