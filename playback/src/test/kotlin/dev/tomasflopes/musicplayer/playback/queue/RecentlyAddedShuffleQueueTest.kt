package dev.tomasflopes.musicplayer.playback.queue

import dev.tomasflopes.musicplayer.core.model.log.PlaySource
import dev.tomasflopes.musicplayer.core.taste.shuffle.ShuffleStrategy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.random.Random

/** The Recently added list gets its own shuffle (design 7.7, ADR 0050); every other source keeps the main one. */
class RecentlyAddedShuffleQueueTest {
    private val sorted = ShuffleStrategy { items, _ -> items.sortedBy { it.songId.value } }

    private fun queueWith(source: PlaySource): PlayQueue =
        PlayQueue(ReverseShuffle, Random(1), recentlyAdded = sorted).apply {
            replace(refs("cab"), 0, source, shuffled = true, randomStart = true)
        }

    @Test
    fun `a shuffled Recently added list is ordered by its own strategy`() {
        assertEquals("abc", queueWith(PlaySource.RECENTLY_ADDED).names())
    }

    @Test
    fun `other sources keep the main strategy`() {
        assertEquals("bac", queueWith(PlaySource.TOP100).names())
    }

    @Test
    fun `without one the main strategy orders Recently added too`() {
        val queue =
            PlayQueue(ReverseShuffle, Random(1)).apply {
                replace(refs("cab"), 0, PlaySource.RECENTLY_ADDED, shuffled = true, randomStart = true)
            }

        assertEquals("bac", queue.names())
    }
}
