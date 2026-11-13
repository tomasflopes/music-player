package dev.tomasflopes.musicplayer.core.taste.shuffle

import dev.tomasflopes.musicplayer.core.model.SongRef
import kotlin.random.Random

/** Uniform random order (Fisher–Yates). The phase 1 shuffle. */
class PlainShuffle : ShuffleStrategy {
    override fun order(
        items: List<SongRef>,
        random: Random,
    ): List<SongRef> = items.shuffled(random)
}
