package dev.tomasflopes.musicplayer.ui.library.folders

import dev.tomasflopes.musicplayer.core.model.Folder
import dev.tomasflopes.musicplayer.core.model.Song

/**
 * One level of the folder tree: [current] is `null` at the music folder. [songs] are the folder's direct songs in
 * display order; Play, Shuffle and a tap all work on exactly this list.
 */
data class FoldersUiState(
    val current: Folder?,
    val folders: List<Folder>,
    val songs: List<Song>,
) {
    val canGoUp: Boolean get() = current != null
}
