package dev.tomasflopes.musicplayer.playback.queue

import dev.tomasflopes.musicplayer.core.model.SongRef
import dev.tomasflopes.musicplayer.core.model.log.PlaySource
import dev.tomasflopes.musicplayer.core.taste.shuffle.ShuffleStrategy
import kotlin.random.Random

/** The ordering rules of [PlayQueue]: how songs are shuffled, unshuffled and relabelled. Stateless. */
internal class QueueOrdering(
    private val strategy: ShuffleStrategy,
    private val random: Random,
    private val artistShuffle: ShuffleStrategy = strategy,
) {
    /** Artist shuffle is the one shuffle without artist spacing (design 7.8), so it gets [artistShuffle]. */
    private fun strategyFor(source: PlaySource?): ShuffleStrategy =
        if (source?.withShuffle(true) == PlaySource.ARTIST_SHUFFLE) artistShuffle else strategy

    /**
     * The strategy's order of [automatic]'s songs, as the same entries, for songs that play right after
     * [preceding] (what the strategy may want to keep clear of).
     */
    fun reorder(
        automatic: List<QueueEntry>,
        preceding: List<SongRef> = emptyList(),
    ): List<QueueEntry> {
        val byRef = automatic.associateBy { it.ref }
        val strategy = strategyFor(automatic.firstOrNull()?.source)
        return strategy.orderAfter(preceding, automatic.map { it.ref }, random).mapNotNull { byRef[it] }
    }

    /**
     * [songs] with [startIndex] first and the rest shuffled; list order when [shuffled] is off. With [randomStart]
     * nothing is first by choice (Shuffle all, a shuffled artist), so the strategy orders the whole list.
     * [manualAhead] are the manual entries that will sit between the first song and the rest.
     */
    fun initial(
        songs: List<SongRef>,
        startIndex: Int,
        source: PlaySource,
        shuffled: Boolean,
        randomStart: Boolean,
        manualAhead: List<SongRef> = emptyList(),
    ): List<SongRef> {
        val strategy = strategyFor(source)
        return when {
            !shuffled -> songs
            randomStart -> strategy.order(songs, random)
            else -> {
                val first = listOfNotNull(songs.getOrNull(startIndex))
                val rest = songs.filterIndexed { i, _ -> i != startIndex }
                first + strategy.orderAfter(first + manualAhead, rest, random)
            }
        }
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
