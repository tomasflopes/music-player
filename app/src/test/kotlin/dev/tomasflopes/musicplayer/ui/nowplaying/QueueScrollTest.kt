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
    fun `rows run played, header, then up next`() {
        val layout = QueueLayout(playedCount = 3)
        assertEquals(0, layout.playedHeaderIndex)
        assertEquals(4, layout.headerIndex)
        assertEquals(6, layout.upNextHeaderIndex)
    }

    @Test
    fun `with nothing played the header is the first row and the initial position`() {
        val layout = QueueLayout(playedCount = 0)
        assertNull(layout.playedHeaderIndex)
        assertEquals(0, layout.headerIndex)
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
