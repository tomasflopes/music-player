package dev.tomasflopes.musicplayer.ui.library.album

import dev.tomasflopes.musicplayer.core.model.Album
import dev.tomasflopes.musicplayer.core.model.Song

/** One line of the track list: a disc header (only on albums with more than one disc) or a track. */
sealed interface AlbumEntry {
    /** Stable list key, unique within the album. */
    val key: String

    data class DiscHeader(
        val disc: Int,
    ) : AlbumEntry {
        override val key: String get() = "disc-$disc"
    }

    /** [index] is the song's place in [AlbumUiState.Ready.songs], which is where playback starts when it is tapped. */
    data class Track(
        val index: Int,
        val song: Song,
    ) : AlbumEntry {
        override val key: String get() = "track-${song.id.value}"
    }
}

sealed interface AlbumUiState {
    /** No album opened yet. */
    data object Loading : AlbumUiState

    /** The route named an album that is not in the library (any more). */
    data object NotFound : AlbumUiState

    /** [songs] are in album order; [cover] is the song whose embedded art stands in for the album's, or `null`. */
    data class Ready(
        val album: Album,
        val cover: Song?,
        val songs: List<Song>,
        val entries: List<AlbumEntry>,
    ) : AlbumUiState
}
