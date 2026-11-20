package dev.tomasflopes.musicplayer.playback.queue

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlayQueueMoveTest {
    @Test
    fun `an upcoming song moves earlier`() {
        val queue = queue("ABCDE")

        assertTrue(queue.move(3, 1))

        assertEquals("ADBCE", queue.names())
        assertEquals('A', queue.current?.name)
        assertEquals(0, queue.index)
    }

    @Test
    fun `an upcoming song moves later`() {
        val queue = queue("ABCDE")

        assertTrue(queue.move(1, 3))

        assertEquals("ACDBE", queue.names())
    }

    @Test
    fun `the current song and the played ones do not move`() {
        val queue = queue("ABCDE", startIndex = 2)

        assertFalse(queue.move(2, 4))
        assertFalse(queue.move(0, 3))
        assertFalse(queue.move(1, 4))

        assertEquals("ABCDE", queue.names())
        assertEquals(2, queue.index)
    }

    @Test
    fun `a song cannot be dropped before the current one`() {
        val queue = queue("ABCDE", startIndex = 1)

        assertTrue(queue.move(3, 0))

        assertEquals("ABDCE", queue.names())
        assertEquals('B', queue.current?.name)
        assertEquals(1, queue.index)
    }

    @Test
    fun `a drop past the end stops at the last place`() {
        val queue = queue("ABCDE")

        assertTrue(queue.move(1, 99))

        assertEquals("ACDEB", queue.names())
    }

    @Test
    fun `moving to the same place or from nowhere changes nothing`() {
        val queue = queue("ABCDE")

        assertFalse(queue.move(2, 2))
        assertFalse(queue.move(9, 1))
        assertFalse(queue.move(-1, 1))

        assertEquals("ABCDE", queue.names())
    }

    @Test
    fun `a move does not start a new queue for the refill`() {
        val queue = queue("ABCDE")
        val before = queue.epoch

        queue.move(3, 1)

        assertEquals(before, queue.epoch)
    }

    @Test
    fun `manual songs stay before auto songs, so a drag stops at the edge of its group`() {
        val queue = queue("ABCDE")
        queue.playNext(refs("F"))
        queue.addToQueue(refs("G"))
        assertEquals("AFGBCDE", queue.names())

        queue.move(5, 0)
        assertEquals("AFGDBCE", queue.names())

        queue.move(1, 6)
        assertEquals("AGFDBCE", queue.names())
    }

    @Test
    fun `a moved song keeps what it is`() {
        val queue = queue("ABCDE")
        queue.playNext(refs("F"))
        queue.addToQueue(refs("G"))

        queue.move(2, 1)

        assertTrue(queue.entryAt(1)?.manual == true)
        assertFalse(queue.entryAt(3)?.manual == true)
    }

    @Test
    fun `turning shuffle off after a reorder keeps the reorder`() {
        val queue = queue("ABCDE")
        queue.move(3, 1)
        assertEquals(refs("ADBCE"), queue.snapshot().originalOrder)

        queue.setShuffled(true)
        queue.setShuffled(false)

        assertEquals("ADBCE", queue.names())
    }

    @Test
    fun `a reorder under shuffle is what shuffle off restores for the songs still to come`() {
        val queue = queue("ABCDE", shuffled = true)
        assertEquals("AEDCB", queue.names())

        queue.move(4, 1)
        assertEquals("ABEDC", queue.names())

        queue.setShuffled(false)

        assertEquals("ABEDC", queue.names())
    }

    @Test
    fun `a reorder rewrites only the slots the upcoming songs hold`() {
        val queue = queue("ABCDE", startIndex = 2)

        queue.move(3, 4)

        assertEquals("ABCED", queue.names())
        assertEquals(refs("ABCED"), queue.snapshot().originalOrder)
    }

    @Test
    fun `reordering manual songs leaves the original order alone`() {
        val queue = queue("ABC")
        queue.addToQueue(refs("DE"))

        queue.move(2, 1)
        assertEquals("AEDBC", queue.names())
        assertEquals(refs("ABC"), queue.snapshot().originalOrder)

        queue.setShuffled(true)
        queue.setShuffled(false)

        assertEquals("AEDBC", queue.names())
    }
}
