package dev.tomasflopes.musicplayer.core.model.undo

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Every [UndoSlot] must pass these. A new implementation's test class extends this one. */
abstract class UndoSlotContract {
    abstract fun slot(): UndoSlot

    @Test
    fun `nothing is pending at first`() {
        assertNull(slot().pending.value)
    }

    @Test
    fun `an offer is pending with its message`() {
        val slot = slot()

        val offer = slot.offer("Removed A") {}

        assertEquals(offer, slot.pending.value)
        assertEquals("Removed A", slot.pending.value?.message)
    }

    @Test
    fun `undo runs the action once and clears the slot`() =
        runBlocking {
            val slot = slot()
            var runs = 0
            val offer = slot.offer("x") { runs++ }

            assertTrue(slot.undo(offer.id))
            assertFalse(slot.undo(offer.id))

            assertEquals(1, runs)
            assertNull(slot.pending.value)
        }

    @Test
    fun `a new offer replaces the pending one without running it`() =
        runBlocking {
            val slot = slot()
            val ran = mutableListOf<String>()
            val first = slot.offer("first") { ran += "first" }
            val second = slot.offer("second") { ran += "second" }

            assertFalse(slot.undo(first.id))
            assertEquals(second, slot.pending.value)
            assertTrue(slot.undo(second.id))

            assertEquals(listOf("second"), ran)
        }

    @Test
    fun `expiring drops the action and never runs it`() =
        runBlocking {
            val slot = slot()
            var runs = 0
            val offer = slot.offer("x") { runs++ }

            slot.expire(offer.id)

            assertNull(slot.pending.value)
            assertFalse(slot.undo(offer.id))
            assertEquals(0, runs)
        }

    @Test
    fun `expiring a replaced offer leaves the newer one pending`() {
        val slot = slot()
        val old = slot.offer("old") {}
        val new = slot.offer("new") {}

        slot.expire(old.id)

        assertEquals(new, slot.pending.value)
    }
}
