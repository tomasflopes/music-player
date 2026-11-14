package dev.tomasflopes.musicplayer.core.model.queue

import dev.tomasflopes.musicplayer.core.model.SongId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class QueueLayoutTest {
    private fun slot(
        index: Int,
        id: Long = index.toLong(),
        manual: Boolean = false,
    ) = QueueSlot(index, SongId(id), manual)

    private val all: (SongId) -> Boolean = { true }

    @Test
    fun `played oldest to newest, then now playing, then up next`() {
        val layout = queueLayoutOf((0..4).map { slot(it) }, currentIndex = 2, known = all)

        assertEquals(listOf(0, 1), layout.played.map { it.index })
        assertEquals(2, layout.current?.index)
        assertEquals(listOf(3, 4), layout.upcoming.map { it.index })
    }

    @Test
    fun `a window that starts late keeps the real indices`() {
        val layout = queueLayoutOf((40..45).map { slot(it) }, currentIndex = 42, known = all)

        assertEquals(listOf(40, 41), layout.played.map { it.index })
        assertEquals(listOf(43, 44, 45), layout.upcoming.map { it.index })
    }

    @Test
    fun `the first song has no history`() {
        val layout = queueLayoutOf((0..2).map { slot(it) }, currentIndex = 0, known = all)

        assertEquals(emptyList<QueueSlot>(), layout.played)
        assertEquals(listOf(1, 2), layout.upcoming.map { it.index })
    }

    @Test
    fun `an empty queue has nothing anywhere`() {
        val layout = queueLayoutOf(emptyList(), currentIndex = -1, known = all)

        assertEquals(emptyList<QueueSlot>(), layout.played)
        assertNull(layout.current)
        assertEquals(emptyList<QueueSlot>(), layout.upcoming)
    }

    @Test
    fun `an unknown song is dropped and the others keep their index`() {
        val layout = queueLayoutOf((0..4).map { slot(it) }, currentIndex = 2) { it.value != 1L && it.value != 3L }

        assertEquals(listOf(0), layout.played.map { it.index })
        assertEquals(listOf(4), layout.upcoming.map { it.index })
    }

    @Test
    fun `a song played earlier and queued again shows in both places`() {
        val window = listOf(slot(0, id = 7), slot(1, id = 8), slot(2, id = 7, manual = true))

        val layout = queueLayoutOf(window, currentIndex = 1, known = all)

        assertEquals(listOf(7L), layout.played.map { it.songId.value })
        assertEquals(listOf(7L), layout.upcoming.map { it.songId.value })
        assertEquals(1, layout.manualUpcoming)
    }

    @Test
    fun `wrapped rows are up next and cannot be addressed`() {
        val window = listOf(slot(3), slot(4), slot(-1, id = 0), slot(-1, id = 1))

        val layout = queueLayoutOf(window, currentIndex = 4, known = all)

        assertEquals(listOf(3), layout.played.map { it.index })
        assertEquals(listOf(-1, -1), layout.upcoming.map { it.index })
    }

    @Test
    fun `manual songs are counted only while they lead the up next list`() {
        val window = listOf(slot(0), slot(1, manual = true), slot(2, manual = true), slot(3), slot(4, manual = true))

        assertEquals(2, queueLayoutOf(window, currentIndex = 0, known = all).manualUpcoming)
    }
}
