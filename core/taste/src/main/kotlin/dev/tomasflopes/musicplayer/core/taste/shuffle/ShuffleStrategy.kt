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
}
