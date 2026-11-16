package dev.tomasflopes.musicplayer.playback.queue

import dev.tomasflopes.musicplayer.core.model.log.PlaySource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.random.Random

class PlayQueueMixTest {
    @Test
    fun `replace stamps the mix on every entry and shuffling keeps it`() {
        val queue = PlayQueue(ReverseShuffle, Random(1))
        queue.replace(refs("ABCD"), 0, PlaySource.MIX, false, mixId = 4L)

        queue.setShuffled(true)

        assertEquals(
            setOf(4L),
            queue
                .snapshot()
                .entries
                .map { it.mixId }
                .toSet(),
        )
    }

    @Test
    fun `a manual entry never carries the mix`() {
        val queue = PlayQueue(ReverseShuffle, Random(1))
        queue.replace(refs("AB"), 0, PlaySource.MIX, false, mixId = 4L)

        queue.addToQueue(refs("Z"))

        assertEquals(listOf(4L, null, 4L), queue.snapshot().entries.map { it.mixId })
    }

    @Test
    fun `the next play replaces the mix`() {
        val queue = PlayQueue(ReverseShuffle, Random(1))
        queue.replace(refs("AB"), 0, PlaySource.MIX, false, mixId = 4L)

        queue.replace(refs("CD"), 0, PlaySource.ALBUM, false)

        assertEquals(listOf(null, null), queue.snapshot().entries.map { it.mixId })
    }
}
