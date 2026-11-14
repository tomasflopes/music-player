package dev.tomasflopes.musicplayer.playback.queue

import dev.tomasflopes.musicplayer.core.model.log.PlaySource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class PlayQueueRemoveTest {
    @Test
    fun `removing a played entry keeps the same song current`() {
        val queue = queue("ABCDE", startIndex = 3)

        queue.remove(1)

        assertEquals("ACDE", queue.names())
        assertEquals('D', queue.current?.name)
        assertEquals(2, queue.index)
    }

    @Test
    fun `removing an upcoming entry leaves the index alone`() {
        val queue = queue("ABCDE", startIndex = 1)

        queue.remove(3)

        assertEquals("ABCE", queue.names())
        assertEquals(1, queue.index)
    }

    @Test
    fun `removing the current entry makes the next one current at the same index`() {
        val queue = queue("ABCDE", startIndex = 2)

        val removed = queue.remove(2)

        assertEquals('C', removed?.name)
        assertEquals('D', queue.current?.name)
        assertEquals(2, queue.index)
    }

    @Test
    fun `removing the last entry while it is current falls back to the one before`() {
        val queue = queue("ABC", startIndex = 2)

        queue.remove(2)

        assertEquals('B', queue.current?.name)
        assertEquals(1, queue.index)
    }

    @Test
    fun `removing the only entry empties the queue`() {
        val queue = queue("A")

        queue.remove(0)

        assertNull(queue.current)
        assertEquals(-1, queue.index)
    }

    @Test
    fun `an index out of range removes nothing`() {
        val queue = queue("ABC")

        assertNull(queue.remove(9))
        assertEquals("ABC", queue.names())
    }

    @Test
    fun `insertAt before the current entry keeps the same song current`() {
        val queue = queue("ABCDE", startIndex = 3)
        val entry = queue.remove(1)!!

        queue.insertAt(1, entry)

        assertEquals("ABCDE", queue.names())
        assertEquals('D', queue.current?.name)
        assertEquals(3, queue.index)
    }

    @Test
    fun `insertAt after the current entry leaves the index alone`() {
        val queue = queue("ABCDE", startIndex = 1)
        val entry = queue.remove(3)!!

        queue.insertAt(3, entry)

        assertEquals("ABCDE", queue.names())
        assertEquals(1, queue.index)
    }

    @Test
    fun `a manual entry comes back manual`() {
        val queue = queue("ABC")
        queue.playNext(refs("X"))
        val entry = queue.remove(1)!!

        queue.insertAt(1, entry)

        assertEquals(true, queue.entryAt(1)?.manual)
        assertEquals(PlaySource.QUEUE, queue.entryAt(1)?.source)
    }

    @Test
    fun `the indexed window gives real indices and no more history than asked`() {
        val queue = queue("ABCDEFGH", startIndex = 5)

        val window = queue.indexedWindow(behind = 2, ahead = 1)

        assertEquals(listOf(3, 4, 5, 6), window.map { it.first })
        assertEquals("DEFG", window.map { it.second.name }.joinToString(""))
    }

    @Test
    fun `wrapped entries under repeat all have no index`() {
        val queue = queue("ABCD", startIndex = 3)
        queue.repeatMode = RepeatMode.ALL

        val window = queue.indexedWindow(behind = 0, ahead = 2)

        assertEquals(listOf(3, -1, -1), window.map { it.first })
    }

    @Test
    fun `with shuffle on the history is the order the songs were played in`() {
        val queue = queue("ABCDE", shuffled = true)
        queue.next()
        queue.next()

        val window = queue.indexedWindow(behind = 10, ahead = 10)
        val playedNames = window.filter { it.first < queue.index }.map { it.second.name }.joinToString("")

        assertEquals(queue.names().take(2), playedNames)
        assertEquals(2, queue.index)
    }
}
