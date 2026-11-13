package dev.tomasflopes.musicplayer.debug

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.tomasflopes.musicplayer.core.model.log.PlayEvent
import dev.tomasflopes.musicplayer.core.model.log.PlaySource
import dev.tomasflopes.musicplayer.core.model.ports.LibraryScanController
import dev.tomasflopes.musicplayer.core.model.ports.MusicRoot
import dev.tomasflopes.musicplayer.core.model.ports.PlayHistoryReader
import dev.tomasflopes.musicplayer.core.model.ports.ScanProgress
import dev.tomasflopes.musicplayer.core.model.ports.SettingsRepository
import dev.tomasflopes.musicplayer.core.model.ports.SongReader
import dev.tomasflopes.musicplayer.data.scan.TreeUriMapper
import dev.tomasflopes.musicplayer.playback.PlayRequest
import dev.tomasflopes.musicplayer.playback.PlaybackController
import dev.tomasflopes.musicplayer.playback.PlaybackState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Debug-only: just enough wiring to pick a folder, scan it and play it, until the real screens exist. */
@HiltViewModel
class DebugViewModel
    @Inject
    constructor(
        private val playback: PlaybackController,
        private val scanner: LibraryScanController,
        private val settings: SettingsRepository,
        private val songs: SongReader,
        history: PlayHistoryReader,
    ) : ViewModel() {
        val playbackState: StateFlow<PlaybackState> = playback.state
        val scan: StateFlow<ScanProgress> = scanner.progress

        val songCount: StateFlow<Int> =
            songs
                .observeSongs()
                .map { it.size }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), 0)

        val recentEvents: StateFlow<List<PlayEvent>> =
            history
                .observeRecent(EVENT_ROWS)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

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

        fun scanNow() = scanner.requestScan()

        fun shuffleAll() {
            viewModelScope.launch {
                val refs = songs.allSongRefs()
                if (refs.isNotEmpty()) {
                    playback.play(PlayRequest(refs, 0, shuffle = true, PlaySource.SHUFFLE_ALL, picked = false))
                }
            }
        }

        fun togglePlayPause() = playback.togglePlayPause()

        fun next() = playback.next()

        fun toggleShuffle() = playback.setShuffle(!playback.state.value.shuffle)

        fun cycleRepeat() = playback.cycleRepeatMode()

        private companion object {
            const val EVENT_ROWS = 50
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }
