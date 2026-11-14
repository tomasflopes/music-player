package dev.tomasflopes.musicplayer.ui.selection

import dev.tomasflopes.musicplayer.core.model.SongId
import dev.tomasflopes.musicplayer.fakes.testSong
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SelectionStateTest {
    private val a = testSong(1)
    private val b = testSong(2)
    private val c = testSong(3)

    @Test
    fun `nothing is selected at first`() {
        val selection = SelectionState()

        assertFalse(selection.active)
        assertEquals(0, selection.count)
    }

    @Test
    fun `a long press starts a selection with that song`() {
        val selection = SelectionState()

        selection.start(a.id)

        assertTrue(selection.active)
        assertTrue(selection.isSelected(a.id))
    }

    @Test
    fun `toggling adds and removes, and the selection ends when the last song leaves`() {
        val selection = SelectionState()
        selection.start(a.id)

        selection.toggle(b.id)
        assertEquals(2, selection.count)

        selection.toggle(a.id)
        selection.toggle(b.id)
        assertFalse(selection.active)
    }

    @Test
    fun `selected songs come back in the order of the list, not the order they were tapped`() {
        val selection = SelectionState()
        selection.start(c.id)
        selection.toggle(a.id)

        assertEquals(listOf(a.id, c.id), selection.idsIn(listOf(a, b, c)))
    }

    @Test
    fun `songs that left the list are not returned`() {
        val selection = SelectionState()
        selection.start(a.id)
        selection.toggle(b.id)

        assertEquals(listOf(b.id), selection.idsIn(listOf(b, c)))
    }

    @Test
    fun `clear ends the selection`() {
        val selection = SelectionState()
        selection.start(a.id)

        selection.clear()

        assertFalse(selection.active)
        assertEquals(emptyList<SongId>(), selection.idsIn(listOf(a)))
    }

    @Test
    fun `a tap on a row selects while a selection is active and plays otherwise`() {
        val selection = SelectionState()
        var played = 0
        selection.onTap(a.id) { played++ }
        selection.start(b.id)
        selection.onTap(a.id) { played++ }

        assertEquals(1, played)
        assertTrue(selection.isSelected(a.id))
    }
}
