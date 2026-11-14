package dev.tomasflopes.musicplayer.core.taste.config

/**
 * The numbers behind smart shuffle (design §7.8, ADR 0035). Kept beside [TasteConfig]; defaults are the owner's.
 */
data class SmartShuffleConfig(
    /** No song by the same main artist among the next [artistGap] tracks. 0 turns the spacing off. */
    val artistGap: Int = 4,
) {
    init {
        require(artistGap in 0..MAX_ARTIST_GAP) { "artistGap must be in 0..$MAX_ARTIST_GAP: $artistGap" }
    }

    companion object {
        const val MAX_ARTIST_GAP = 100
    }
}
