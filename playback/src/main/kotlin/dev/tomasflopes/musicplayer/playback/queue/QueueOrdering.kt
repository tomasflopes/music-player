package dev.tomasflopes.musicplayer.playback.queue

import dev.tomasflopes.musicplayer.core.model.SongRef
import dev.tomasflopes.musicplayer.core.taste.shuffle.ShuffleStrategy
import kotlin.random.Random

/** The ordering rules of [PlayQueue]: how songs are shuffled, unshuffled and relabelled. Stateless. */
internal class QueueOrdering(
    private val strategy: ShuffleStrategy,
    private val random: Random,
) {
    /** The strategy's order of [automatic]'s songs, as the same entries. */
    fun reorder(automatic: List<QueueEntry>): List<QueueEntry> {
        val byRef = automatic.associateBy { it.ref }
        return strategy.order(automatic.map { it.ref }, random).mapNotNull { byRef[it] }
    }

    /**
     * [songs] with [startIndex] first and the rest shuffled; list order when [shuffled] is off. With [randomStart]
     * nothing is first by choice (Shuffle all, a shuffled artist), so the strategy orders the whole list.
     */
    fun initial(
        songs: List<SongRef>,
        startIndex: Int,
        shuffled: Boolean,
        randomStart: Boolean,
    ): List<SongRef> =
        when {
            !shuffled -> songs
            randomStart -> strategy.order(songs, random)
            else ->
                listOfNotNull(songs.getOrNull(startIndex)) +
                    strategy.order(songs.filterIndexed { i, _ -> i != startIndex }, random)
        }

    /** The non-manual entries of [all] back in [originalOrder]. */
    fun restoreOriginal(
        all: List<QueueEntry>,
        originalOrder: List<SongRef>,
    ): List<QueueEntry> {
        val byRef = all.filter { !it.manual }.associateBy { it.ref }
        return originalOrder.mapNotNull { byRef[it] }
    }

    /** Only non-manual entries change label; QUEUE stays QUEUE. */
    fun relabel(
        entries: List<QueueEntry>,
        shuffled: Boolean,
    ): List<QueueEntry> = entries.map { if (it.manual) it else it.copy(source = it.source.withShuffle(shuffled)) }
}
