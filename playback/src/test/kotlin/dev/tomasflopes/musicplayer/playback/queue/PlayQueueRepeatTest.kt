package dev.tomasflopes.musicplayer.playback.queue

import dev.tomasflopes.musicplayer.core.model.log.PlaySource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class PlayQueueRepeatTest {
    @Test
    fun `off stops at the end`() {
        val queue = queue("AB", startIndex = 1)

        assertNull(queue.next())
        assertNull(queue.advanceOnTrackEnd())
        assertEquals(1, queue.index)
        assertEquals('B', queue.current?.name)
    }

    @Test
    fun `all wraps to the first song`() {
        val queue = queue("AB", startIndex = 1)
        queue.repeatMode = RepeatMode.ALL

        assertEquals('A', queue.advanceOnTrackEnd()?.name)
        assertEquals(0, queue.index)
        queue.next()
        assertEquals('A', queue.next()?.name)
    }

    @Test
    fun `all with shuffle reshuffles on wrap`() {
        val queue = queue("ABC", startIndex = 0, shuffled = true)
        queue.repeatMode = RepeatMode.ALL
        assertEquals("ACB", queue.names())
        queue.next()
        queue.next()

        queue.advanceOnTrackEnd()

        assertEquals("BCA", queue.names())
        assertEquals(0, queue.index)
        assertEquals('B', queue.current?.name)
    }

    @Test
    fun `one repeats the same song when it ends`() {
        val queue = queue("AB")
        queue.repeatMode = RepeatMode.ONE
        val current = queue.current

        assertSame(current, queue.advanceOnTrackEnd())
        assertEquals(0, queue.index)
    }

    @Test
    fun `one still moves on when the user presses next`() {
        val queue = queue("AB")
        queue.repeatMode = RepeatMode.ONE

        assertEquals('B', queue.next()?.name)
        assertEquals('A', queue.next()?.name)
    }

    @Test
    fun `window wraps under repeat all`() {
        val queue = queue("ABCDE", startIndex = 4)
        queue.repeatMode = RepeatMode.ALL

        assertEquals("EABC", queue.window(behind = 0, ahead = 3).map { it.name }.joinToString(""))
    }

    @Test
    fun `window does not wrap under repeat off or one`() {
        val queue = queue("ABCDE", startIndex = 4)

        assertEquals("E", queue.window(0, 3).map { it.name }.joinToString(""))
        queue.repeatMode = RepeatMode.ONE
        assertEquals("E", queue.window(0, 3).map { it.name }.joinToString(""))
    }

    @Test
    fun `repeat mode survives replace`() {
        val queue = queue("AB")
        queue.repeatMode = RepeatMode.ONE

        queue.replace(refs("CD"), 0, PlaySource.ALBUM, shuffled = false)

        assertEquals(RepeatMode.ONE, queue.repeatMode)
    }

    @Test
    fun `previous at the start wraps under repeat all`() {
        val queue = queue("ABC")
        queue.repeatMode = RepeatMode.ALL

        assertEquals('C', queue.previous()?.name)
    }
}
