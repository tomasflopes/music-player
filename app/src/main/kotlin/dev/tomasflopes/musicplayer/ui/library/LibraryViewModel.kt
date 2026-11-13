package dev.tomasflopes.musicplayer.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.tomasflopes.musicplayer.core.model.Song
import dev.tomasflopes.musicplayer.core.model.log.PlaySource
import dev.tomasflopes.musicplayer.core.model.ports.LibraryScanController
import dev.tomasflopes.musicplayer.core.model.ports.MusicRoot
import dev.tomasflopes.musicplayer.core.model.ports.ScanProgress
import dev.tomasflopes.musicplayer.core.model.ports.SettingsRepository
import dev.tomasflopes.musicplayer.core.model.ports.SongReader
import dev.tomasflopes.musicplayer.core.model.ref
import dev.tomasflopes.musicplayer.data.scan.TreeUriMapper
import dev.tomasflopes.musicplayer.playback.PlayRequest
import dev.tomasflopes.musicplayer.playback.PlaybackController
import dev.tomasflopes.musicplayer.playback.PlaybackState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel
    @Inject
    constructor(
        private val playback: PlaybackController,
        private val scanner: LibraryScanController,
        private val settings: SettingsRepository,
        songReader: SongReader,
    ) : ViewModel() {
        val songs: StateFlow<List<Song>> =
            songReader.observeSongs().stateIn(viewModelScope, WhileSubscribed, emptyList())
        val musicRoot: StateFlow<MusicRoot?> = settings.musicRoot.stateIn(viewModelScope, WhileSubscribed, null)
        val scan: StateFlow<ScanProgress> = scanner.progress
        val player: StateFlow<PlaybackState> = playback.state

        fun folderPicked(
            treeUri: String,
            documentId: String,
        ) {
            val location = TreeUriMapper.fromTreeDocumentId(documentId) ?: return
            viewModelScope.launch {
                settings.setMusicRoot(MusicRoot(treeUri, location.volumeName, location.relativePath))
                scanner.requestScan()
            }
        }

        fun rescan() = scanner.requestScan()

        fun playFrom(index: Int) {
            val list = songs.value
            if (index !in list.indices) return
            playback.play(PlayRequest(list.map { it.ref }, index, shuffle = false, PlaySource.SONGS, picked = true))
        }

        fun shuffleAll() {
            val list = songs.value
            if (list.isEmpty()) return
            playback.play(PlayRequest(list.map { it.ref }, 0, shuffle = true, PlaySource.SHUFFLE_ALL, picked = false))
        }

        fun togglePlayPause() = playback.togglePlayPause()

        fun next() = playback.next()

        fun previous() = playback.previous()

        fun seekTo(positionMs: Long) = playback.seekTo(positionMs)

        private companion object {
            val WhileSubscribed = SharingStarted.WhileSubscribed(5_000L)
        }
    }
