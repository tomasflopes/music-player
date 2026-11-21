package dev.tomasflopes.musicplayer.core.taste.mix

import dev.tomasflopes.musicplayer.core.model.SongId

/**
 * What one mix learned from its own log rows (design §7.4 and §7.6, ADR 0085): the input of [MixEngine] besides the
 * seeds. Plain values, so the engine stays pure. [EMPTY] is a mix with no feedback: the engine then builds exactly the
 * batch it built before learning existed.
 */
data class MixLearning(
    /**
     * Songs with a mix score of at least `likedMinScore`, best score first and then the most recent. Not gated and not
     * capped: the engine does both, because only it knows where a song sits against the current seeds.
     */
    val liked: List<SongId> = emptyList(),
    /** Songs with a mix score at or below `bannedScore`; they are not candidates, opening songs included. */
    val banned: Set<SongId> = emptySet(),
    /** Songs played in this mix inside the cool-down window, whatever the outcome. */
    val playedInMix: Set<SongId> = emptySet(),
    /** Rows of this mix that said something (not neutral) and were not burst-softened; drives exploration. */
    val informativeRows: Int = 0,
) {
    init {
        require(informativeRows >= 0) { "informativeRows must not be negative: $informativeRows" }
    }

    companion object {
        val EMPTY = MixLearning()
    }
}
