package dev.tomasflopes.musicplayer.playback.queue

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import kotlin.random.Random

class PlayQueuePeekAdvanceTest {
    @Test
    fun `is the next song, and moves nothing`() {
        val queue = queue("ABC")

        assertEquals('B', queue.peekAdvance()?.name)
        assertEquals(0, queue.index)
    }

    @Test
    fun `is the same as what advancing on a track end then returns`() {
        val queue = queue("ABC", startIndex = 1)

        val peeked = queue.peekAdvance()

        assertEquals(peeked, queue.advanceOnTrackEnd())
    }

    @Test
    fun `is nothing at the end of the queue with repeat off`() {
        assertNull(queue("ABC", startIndex = 2).peekAdvance())
    }

    @Test
    fun `is nothing for an empty queue`() {
        assertNull(PlayQueue(ReverseShuffle, Random(1)).peekAdvance())
    }

    @Test
    fun `is the current song under repeat one`() {
        val queue = queue("ABC", startIndex = 1)
        queue.repeatMode = RepeatMode.ONE

        assertEquals('B', queue.peekAdvance()?.name)
    }

    @Test
    fun `wraps to the first song under repeat all`() {
        val queue = queue("ABC", startIndex = 2)
        queue.repeatMode = RepeatMode.ALL

        assertEquals('A', queue.peekAdvance()?.name)
    }

    @Test
    fun `cannot know the song after a shuffled wrap, which reshuffles`() {
        val queue = queue("ABC", shuffled = true)
        queue.jumpTo(2)
        queue.repeatMode = RepeatMode.ALL

        assertNull(queue.peekAdvance())
    }

    @Test
    fun `sees a song queued by hand first`() {
        val queue = queue("ABC")
        queue.playNext(refs("Z"))

        assertEquals('Z', queue.peekAdvance()?.name)
    }
}
