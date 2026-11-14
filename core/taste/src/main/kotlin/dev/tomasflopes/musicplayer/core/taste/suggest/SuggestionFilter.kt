package dev.tomasflopes.musicplayer.core.taste.suggest

import dev.tomasflopes.musicplayer.core.model.Song

/**
 * One reason to suggest a song, or to rank it higher or lower (design 7.13). Extension point: a new rule is a new
 * implementation registered with a Hilt multibinding, and the sound-based checks of phases 3 and 4 arrive that way.
 *
 * Rules every implementation keeps (the contract test checks them): [points] is finite, gives the same answer for
 * the same input, never throws, and a relevance check never gives negative points and gives none when the playlist
 * is empty.
 */
interface SuggestionFilter {
    /** Stable and unique; filters are applied in the order of their ids. */
    val id: String

    /**
     * `true` when points from this filter make a song relevant ("makes sense"). Filters that only rank (taste, what
     * the owner dismissed) are `false`: they can never make a song appear on their own.
     */
    val isRelevanceCheck: Boolean

    /** Points for [candidate], which is neither in the playlist nor dismissed. Negative points push it down. */
    fun points(
        candidate: Song,
        context: SuggestionContext,
    ): Double
}
