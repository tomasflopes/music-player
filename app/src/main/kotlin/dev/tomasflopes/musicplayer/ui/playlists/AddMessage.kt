package dev.tomasflopes.musicplayer.ui.playlists

import dev.tomasflopes.musicplayer.core.model.playlists.AddResult

/** The one line the owner sees after adding songs to a playlist (ADR 0039). */
object AddMessage {
    fun of(
        result: AddResult,
        playlistName: String,
    ): String =
        when {
            result.added == 0 && result.alreadyThere == 0 -> "Nothing to add"
            result.added == 0 && result.alreadyThere == 1 -> "Already in $playlistName"
            result.added == 0 -> "All ${result.alreadyThere} already in $playlistName"
            result.alreadyThere > 0 -> "Added ${result.added}, ${result.alreadyThere} already there"
            result.added == 1 -> "Added to $playlistName"
            else -> "Added ${result.added} songs to $playlistName"
        }
}
