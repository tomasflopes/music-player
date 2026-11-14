package dev.tomasflopes.musicplayer.core.taste.shuffle

import dev.tomasflopes.musicplayer.core.model.SongRef
import kotlin.random.Random

/**
 * Puts songs in play order for shuffle. Extension point: smart shuffle (phase 2) is a second implementation;
 * this one stays so both can be compared. Must not change [items].
 */
fun interface ShuffleStrategy {
    fun order(
        items: List<SongRef>,
        random: Random,
    ): List<SongRef>

    /**
     * Like [order] for items that will play right after [preceding] (oldest first), which an order may want to
     * stay clear of. Only the last few of [preceding] can matter. Defaults to [order].
     */
    fun orderAfter(
        preceding: List<SongRef>,
        items: List<SongRef>,
        random: Random,
    ): List<SongRef> = order(items, random)
}
