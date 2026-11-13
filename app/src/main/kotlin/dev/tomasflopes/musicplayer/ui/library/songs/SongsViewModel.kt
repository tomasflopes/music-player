package dev.tomasflopes.musicplayer.ui.library.songs

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
import dev.tomasflopes.musicplayer.ui.library.IndexedList
import dev.tomasflopes.musicplayer.ui.library.indexed
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** The Songs sub-tab of Library: every song by title, with the A-Z sections computed once per library snapshot. */
@HiltViewModel
class SongsViewModel
    @Inject
    constructor(
        private val playback: PlaybackController,
        songReader: SongReader,
    ) : ViewModel() {
        /** `null` until the first read, so an empty library and a loading one look different. */
        val songs: StateFlow<IndexedList<Song>?> =
            songReader
                .observeSongs()
                .indexed { it.title }
                .stateIn(viewModelScope, WhileSubscribed, null)

        /** Plays the list in its shown order, starting at the tapped song. */
        fun onSongClick(index: Int) {
            val shown = songs.value?.items ?: return
            if (index !in shown.indices) return
            playback.play(PlayRequest(shown.map { it.ref }, index, shuffle = false, PlaySource.SONGS, picked = true))
        }

        fun onPlayNext(song: Song) = playback.playNext(listOf(song.ref))

        fun onAddToQueue(song: Song) = playback.addToQueue(listOf(song.ref))
    }
