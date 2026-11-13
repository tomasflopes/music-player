package dev.tomasflopes.musicplayer.core.model.lists

import dev.tomasflopes.musicplayer.core.model.Song
import dev.tomasflopes.musicplayer.core.model.log.PlaySource
import kotlinx.coroutines.flow.Flow

/**
 * A list of songs the app builds by itself (Recently added now; Top 100, Discover and Rewind later).
 * Extension point: a new list is a new implementation registered with a Hilt multibinding.
 */
interface DynamicListProvider {
    /** Stable: used in routes and saved state. */
    val id: String
    val title: String

    /** Position among the dynamic lists; lower comes first. */
    val order: Int

    /** What plays started from this list are logged as. */
    val playSource: PlaySource

    fun songs(): Flow<List<Song>>
}
