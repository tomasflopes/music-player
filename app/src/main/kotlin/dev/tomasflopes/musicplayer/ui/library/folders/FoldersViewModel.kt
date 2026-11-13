package dev.tomasflopes.musicplayer.ui.library.folders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.tomasflopes.musicplayer.core.model.Folder
import dev.tomasflopes.musicplayer.core.model.Song
import dev.tomasflopes.musicplayer.core.model.log.PlaySource
import dev.tomasflopes.musicplayer.core.model.ports.FolderReader
import dev.tomasflopes.musicplayer.core.model.ports.SettingsRepository
import dev.tomasflopes.musicplayer.core.model.ports.SongReader
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
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/**
 * The Folders sub-tab of Library (design 8.2): the tree from the music folder, folders first, then the folder's
 * direct songs. The folders opened so far are kept here, so back restores the parent. Everything plays as
 * [PlaySource.FOLDER] and only the folder's own songs, never those of its subfolders; Play and a tapped song keep the
 * shown order, only Shuffle shuffles.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class FoldersViewModel
    @Inject
    constructor(
        private val playback: PlaybackController,
        private val folderReader: FolderReader,
        private val songReader: SongReader,
        private val settings: SettingsRepository,
    ) : ViewModel() {
        /** The folders entered, outermost first; empty at the music folder. */
        private val trail = MutableStateFlow<List<Folder>>(emptyList())

        /** `null` until the first read, so an empty library and a loading one look different. */
        val state: StateFlow<FoldersUiState?> =
            trail
                .flatMapLatest { opened -> render(opened.lastOrNull()) }
                .stateIn(viewModelScope, WhileSubscribed, null)

        fun onOpenFolder(folder: Folder) = trail.update { it + folder }

        /** Goes up one level. `false` at the music folder, so the caller lets the system handle back. */
        fun onBack(): Boolean {
            var handled = false
            trail.update { opened ->
                handled = opened.isNotEmpty()
                opened.dropLast(1)
            }
            return handled
        }

        fun onPlay() = start(startIndex = 0, shuffle = false, picked = false)

        fun onShuffle() = start(startIndex = 0, shuffle = true, picked = false)

        /** Plays the folder's songs in the shown order, starting at the tapped one. */
        fun onSongClick(index: Int) = start(startIndex = index, shuffle = false, picked = true)

        fun onPlayNext(song: Song) = playback.playNext(listOf(song.ref))

        fun onAddToQueue(song: Song) = playback.addToQueue(listOf(song.ref))

        private fun start(
            startIndex: Int,
            shuffle: Boolean,
            picked: Boolean,
        ) {
            val songs = state.value?.songs ?: return
            if (startIndex !in songs.indices) return
            playback.play(PlayRequest(songs.map { it.ref }, startIndex, shuffle, PlaySource.FOLDER, picked))
        }

        private fun render(folder: Folder?): Flow<FoldersUiState> =
            combine(folderReader.observeChildren(folder?.path), songsIn(folder)) { children, songs ->
                FoldersUiState(folder, children, songs)
            }

        /** The music folder has no row of its own, so its path comes from the saved root (`""` or `Music/`). */
        private fun songsIn(folder: Folder?): Flow<List<Song>> =
            if (folder != null) {
                songReader.observeFolderSongs(folder.path)
            } else {
                settings.musicRoot.flatMapLatest { root ->
                    if (root == null) {
                        flowOf(emptyList())
                    } else {
                        songReader.observeFolderSongs(root.relativePath.trimEnd('/'))
                    }
                }
            }
    }
