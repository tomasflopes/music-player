package dev.tomasflopes.musicplayer.ui.nowplaying

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import dev.tomasflopes.musicplayer.core.model.SongId

/**
 * Where the rows of the queue list sit, top to bottom: the played songs (dimmed, under a "Played" header), the
 * Now playing header, its save button and "Up next". The played songs are above the header, so the list opens on
 * the header with them scrolled out of sight, "Up next" right under it, and scrolls back to the header whenever the
 * current song changes.
 */
internal class QueueLayout(
    private val playedCount: Int,
) {
    /** The row of the played songs' own header, or `null` when nothing has been played. */
    val playedHeaderIndex: Int? get() = if (playedCount > 0) 0 else null

    /** The row of the Now playing header: the list's initial position and its scroll target. */
    val headerIndex: Int get() = if (playedCount > 0) 1 + playedCount else 0

    val upNextHeaderIndex: Int get() = headerIndex + 2
}

/**
 * Says when the list must scroll back to the header: when the current song is not the one seen before, whether the
 * owner tapped a row or pressed next or previous. The first song seen, and a repeat of the same song, do not scroll.
 */
internal class HeaderScroll {
    private var last: SongId? = null

    fun shouldScrollTo(current: SongId): Boolean {
        val previous = last
        last = current
        return previous != null && previous != current
    }
}

/**
 * Takes whatever scroll the queue list did not use, so the sheet around it never receives it. Without this, a drag
 * that reaches the end of the list moves the sheet and can dismiss it; the sheet is still dragged by its handle and
 * closed by Back.
 */
internal class KeepScrollInList : NestedScrollConnection {
    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource,
    ): Offset = available

    override suspend fun onPostFling(
        consumed: Velocity,
        available: Velocity,
    ): Velocity = available
}
