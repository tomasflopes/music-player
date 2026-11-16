package dev.tomasflopes.musicplayer.ui.nowplaying

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import dev.tomasflopes.musicplayer.core.model.SongId

/**
 * Where the rows of the queue list sit, top to bottom: the Now playing header, its save button, the played songs
 * (dimmed, under a "Played" header) and then "Up next". The header is always first, so it is also the row the list
 * scrolls to when the current song changes.
 */
internal class QueueLayout(
    private val playedCount: Int,
) {
    /** The row of the played songs' own header, or `null` when nothing has been played. */
    val playedHeaderIndex: Int? get() = if (playedCount > 0) SAVE_INDEX + 1 else null

    /** The first played song's row, or `null` when nothing has been played. */
    val firstPlayedIndex: Int? get() = playedHeaderIndex?.plus(1)

    val upNextHeaderIndex: Int get() = if (playedCount > 0) SAVE_INDEX + 1 + 1 + playedCount else SAVE_INDEX + 1

    companion object {
        const val HEADER_INDEX = 0
        private const val SAVE_INDEX = 1
    }
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
