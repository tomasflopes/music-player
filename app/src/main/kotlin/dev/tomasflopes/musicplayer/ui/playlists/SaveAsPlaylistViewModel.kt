package dev.tomasflopes.musicplayer.ui.playlists

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.tomasflopes.musicplayer.core.model.SongId
import dev.tomasflopes.musicplayer.core.model.log.PlaySource
import dev.tomasflopes.musicplayer.core.model.playlists.SaveAsPlaylist
import dev.tomasflopes.musicplayer.playback.PlaybackController
import javax.inject.Inject

/** "Save as playlist" for a list screen or the queue; each call answers with the message to show (ADR 0044). */
@HiltViewModel
class SaveAsPlaylistViewModel
    @Inject
    constructor(
        private val save: SaveAsPlaylist,
        private val playback: PlaybackController,
    ) : ViewModel() {
        suspend fun saveList(
            name: String,
            songs: List<SongId>,
            source: PlaySource,
        ): String = message(save(name, songs, listOf(source)), name)

        /** The whole queue from the current song on, read now, not when the dialog was opened. */
        suspend fun saveQueue(name: String): String {
            val queue = playback.queueContent()
            return message(save(name, queue.songs, queue.sources), name)
        }

        private fun message(
            result: SaveAsPlaylist.Result,
            name: String,
        ): String =
            when (result) {
                is SaveAsPlaylist.Result.Saved -> "Saved ${songCount(result.added)} as ${name.trim()}"
                SaveAsPlaylist.Result.ShuffleAll -> "Shuffle all can't be saved as a playlist"
                is SaveAsPlaylist.Result.TooManySongs -> "A saved playlist holds up to ${result.max} songs"
                SaveAsPlaylist.Result.Empty -> "Nothing to save"
                SaveAsPlaylist.Result.InvalidName -> "That is not a valid name"
            }

        private fun songCount(count: Int) = if (count == 1) "1 song" else "$count songs"
    }
