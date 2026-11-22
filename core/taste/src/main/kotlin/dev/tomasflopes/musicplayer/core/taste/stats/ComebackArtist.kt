package dev.tomasflopes.musicplayer.core.taste.stats

import dev.tomasflopes.musicplayer.core.model.ArtistId
import dev.tomasflopes.musicplayer.core.model.stats.ArtistPlays
import dev.tomasflopes.musicplayer.core.taste.config.StatsConfig

data class Comeback(
    val artistId: ArtistId,
    val plays: Int,
    val previousPlays: Int,
) {
    val rise: Int get() = plays - previousPlays
}

/**
 * The artist whose plays rose most against the previous period (design §7.10). An artist missing from the previous
 * period rose from nothing. Ties go to the artist with more plays now, then to the lower id. With nothing played in
 * the previous period there is nothing to compare, so there is no comeback.
 */
class ComebackArtist(
    private val config: StatsConfig,
) {
    fun of(
        current: List<ArtistPlays>,
        previous: List<ArtistPlays>,
    ): Comeback? {
        if (previous.isEmpty()) return null
        val before = previous.associate { it.artistId to it.plays }
        return current
            .map { Comeback(it.artistId, it.plays, before[it.artistId] ?: 0) }
            .filter { it.rise >= config.comebackMinRise }
            .minWithOrNull(
                compareByDescending<Comeback> { it.rise }.thenByDescending { it.plays }.thenBy { it.artistId.value },
            )
    }
}
