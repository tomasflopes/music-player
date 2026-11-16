package dev.tomasflopes.musicplayer.ui.nowplaying

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import dev.tomasflopes.musicplayer.core.model.SongId
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class QueueScrollTest {
    @Test
    fun `the header is the first row, so the initial position shows it and the current song`() {
        assertEquals(0, QueueLayout.HEADER_INDEX)
    }

    @Test
    fun `rows run header, played, then up next`() {
        val layout = QueueLayout(playedCount = 3)
        assertEquals(2, layout.playedHeaderIndex)
        assertEquals(3, layout.firstPlayedIndex)
        assertEquals(6, layout.upNextHeaderIndex)
        assertTrue(QueueLayout.HEADER_INDEX < layout.playedHeaderIndex!!)
        assertTrue(layout.firstPlayedIndex!! < layout.upNextHeaderIndex)
    }

    @Test
    fun `with nothing played, up next follows the header block`() {
        val layout = QueueLayout(playedCount = 0)
        assertNull(layout.playedHeaderIndex)
        assertNull(layout.firstPlayedIndex)
        assertEquals(2, layout.upNextHeaderIndex)
    }

    @Test
    fun `the first song seen does not scroll`() {
        assertFalse(HeaderScroll().shouldScrollTo(SongId(1)))
    }

    @Test
    fun `a new current song scrolls to the header, a repeat of the same one does not`() {
        val scroll = HeaderScroll()
        scroll.shouldScrollTo(SongId(1))
        assertTrue(scroll.shouldScrollTo(SongId(2)))
        assertFalse(scroll.shouldScrollTo(SongId(2)))
        assertTrue(scroll.shouldScrollTo(SongId(1)))
    }

    @Test
    fun `scroll the list did not use is taken, so the sheet never gets it`() =
        runTest {
            val connection = KeepScrollInList()
            val left = Offset(0f, 40f)
            assertEquals(left, connection.onPostScroll(Offset.Zero, left, NestedScrollSource.UserInput))
            val velocity = Velocity(0f, 900f)
            assertEquals(velocity, connection.onPostFling(Velocity.Zero, velocity))
        }
}
