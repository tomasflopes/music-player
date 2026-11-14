package dev.tomasflopes.musicplayer.core.taste.suggest

import dev.tomasflopes.musicplayer.core.model.Song

/**
 * Applies every [SuggestionFilter] to the candidates and keeps the best [limit]. A song needs points from at least
 * one relevance check and a total above zero, so a pure random pick is never shown (design 7.13). Best first; ties go
 * to the lower song id so the same input always gives the same list.
 */
class SuggestionRanker(
    filters: Set<SuggestionFilter>,
) {
    private val filters = filters.sortedBy { it.id }

    private class Scored(
        val song: Song,
        val points: Double,
    )

    fun rank(
        candidates: List<Song>,
        context: SuggestionContext,
        limit: Int,
    ): List<Song> =
        candidates
            .mapNotNull { score(it, context) }
            .sortedWith(compareByDescending<Scored> { it.points }.thenBy { it.song.id.value })
            .take(limit)
            .map { it.song }

    private fun score(
        song: Song,
        context: SuggestionContext,
    ): Scored? {
        var total = 0.0
        var relevant = false
        for (filter in filters) {
            val points = filter.points(song, context)
            total += points
            if (filter.isRelevanceCheck && points > 0.0) relevant = true
        }
        return if (relevant && total > 0.0) Scored(song, total) else null
    }
}
