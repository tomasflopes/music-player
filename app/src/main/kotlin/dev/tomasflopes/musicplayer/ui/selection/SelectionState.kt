package dev.tomasflopes.musicplayer.ui.selection

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.tomasflopes.musicplayer.core.model.Song
import dev.tomasflopes.musicplayer.core.model.SongId

/**
 * The songs ticked by a long press (design 7.12, ADR 0040). One per screen, held by the composable: a selection is
 * about what is on screen, so it needs no ViewModel and goes away with the screen. While it is [active], a tap on a
 * row ticks it instead of playing.
 */
class SelectionState {
    private var ids by mutableStateOf<Set<SongId>>(emptySet())

    val active: Boolean get() = ids.isNotEmpty()
    val count: Int get() = ids.size

    fun isSelected(id: SongId): Boolean = id in ids

    fun start(id: SongId) {
        ids = setOf(id)
    }

    fun toggle(id: SongId) {
        ids = if (id in ids) ids - id else ids + id
    }

    fun clear() {
        ids = emptySet()
    }

    /** A long press starts a selection, or ticks one more row when there is one already. */
    fun onLongPress(id: SongId) = if (active) toggle(id) else start(id)

    /** A tap ticks while selecting; otherwise it is the row's own action. */
    fun onTap(
        id: SongId,
        action: () -> Unit,
    ) = if (active) toggle(id) else action()

    /** The ticked songs that are still in [songs], in the order of [songs]. */
    fun idsIn(songs: List<Song>): List<SongId> = songs.map { it.id }.filter { it in ids }
}
