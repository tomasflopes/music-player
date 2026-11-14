package dev.tomasflopes.musicplayer.playback.queue

import dev.tomasflopes.musicplayer.core.model.SongRef
import dev.tomasflopes.musicplayer.core.taste.shuffle.ShuffleStrategy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.random.Random

/** Restoring a saved queue must not shuffle again (ADR 0035: smart shuffle is not re-run on restore). */
class PlayQueueRestoreTest {
    private object NoShuffleAllowed : ShuffleStrategy {
        override fun order(
            items: List<SongRef>,
            random: Random,
        ): List<SongRef> = error("restore must not shuffle")
    }

    private fun restoredFromDisk(source: PlayQueue): PlayQueue {
        val bytes = QueueCodec.encode(SavedQueue(source.snapshot(), 12_000L))
        val restored = QueueCodec.decode(bytes)!!
        return PlayQueue(NoShuffleAllowed, Random(2)).also { it.restore(restored.snapshot) }
    }

    @Test
    fun `a restored queue has the same order, current song, manual entries and modes`() {
        val before = queue("ABCDEF", startIndex = 0, shuffled = true)
        before.next()
        before.playNext(refs("X"))
        before.addToQueue(refs("Y"))
        before.repeatMode = RepeatMode.ALL

        val after = restoredFromDisk(before)

        assertEquals(before.names(), after.names())
        assertEquals(before.index, after.index)
        assertEquals(before.current, after.current)
        assertEquals(before.shuffled, after.shuffled)
        assertEquals(RepeatMode.ALL, after.repeatMode)
        assertEquals(
            listOf(true, true),
            after
                .window(0, 10)
                .drop(1)
                .take(2)
                .map { it.manual },
        )
    }

    @Test
    fun `moving on and turning shuffle off after a restore never call the strategy`() {
        val before = queue("ABCDEF", shuffled = true)
        val after = restoredFromDisk(before)

        after.next()
        after.setShuffled(false)

        assertEquals("ABCDEF", after.names())
    }
}
