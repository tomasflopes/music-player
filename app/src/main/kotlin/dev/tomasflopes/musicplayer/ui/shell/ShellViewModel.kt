package dev.tomasflopes.musicplayer.ui.shell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.tomasflopes.musicplayer.core.model.ports.LibraryScanController
import dev.tomasflopes.musicplayer.core.model.ports.MusicRootResolver
import dev.tomasflopes.musicplayer.core.model.ports.SettingsRepository
import dev.tomasflopes.musicplayer.ui.WhileSubscribed
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What the app shows first: nothing while the saved folder loads (no flash of the prompt), the prompt, or the tabs. */
enum class ShellState { LOADING, NEEDS_FOLDER, READY }

@HiltViewModel
class ShellViewModel
    @Inject
    constructor(
        private val settings: SettingsRepository,
        private val resolver: MusicRootResolver,
        private val scanner: LibraryScanController,
    ) : ViewModel() {
        val state: StateFlow<ShellState> =
            settings.musicRoot
                .map { root -> if (root == null) ShellState.NEEDS_FOLDER else ShellState.READY }
                .stateIn(viewModelScope, WhileSubscribed, ShellState.LOADING)

        /** The folder picker returned [treeUri]; a folder MediaStore does not list is ignored and the prompt stays. */
        fun folderPicked(
            treeUri: String,
            treeDocumentId: String,
        ) {
            val root = resolver.resolve(treeUri, treeDocumentId) ?: return
            viewModelScope.launch {
                settings.setMusicRoot(root)
                scanner.requestScan()
            }
        }
    }
