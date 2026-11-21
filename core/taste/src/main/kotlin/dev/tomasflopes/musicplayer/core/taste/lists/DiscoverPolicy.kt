package dev.tomasflopes.musicplayer.core.taste.lists

import dev.tomasflopes.musicplayer.core.model.ArtistId
import dev.tomasflopes.musicplayer.core.model.Song
import dev.tomasflopes.musicplayer.core.model.SongId
import dev.tomasflopes.musicplayer.core.taste.config.DiscoverConfig
import kotlin.math.tanh
import kotlin.random.Random

/**
 * Picks the Discover list from what the listening log and the tags say (design §7.7, ADR 0063). Pure Kotlin.
 *
 * 1. Candidates are the songs with no `play_event` row at all, counting a copy of the same recording as the same song
 *    and listing only one copy of it (ADR 0070).
 * 2. Each gets a rank: its main artist's squashed taste score plus a small random part. The random part is a hash of
 *    one draw from [Random] and the song id, so a given seed always gives the same list and a song that leaves the
 *    candidates (because it was just played) does not move the others.
 * 3. The best-ranked songs are kept, at most [DiscoverConfig.maxPerArtist] per main artist, up to
 *    [DiscoverConfig.size]. The cap is hard: a library of one artist gives a list of `maxPerArtist` songs.
 * 4. They are spread so that no main artist repeats within [DiscoverConfig.artistGap] songs, keeping rank order
 *    otherwise. When every artist left is inside the gap, the one placed longest ago goes first (the gap relaxes).
 *
 * Sound similarity replaces step 2 later; steps 1, 3 and 4 stay.
 */
class DiscoverPolicy(
    private val config: DiscoverConfig = DiscoverConfig(),
) : DiscoverRule {
    /** The [DiscoverRule] form (ADR 0071): this policy reads only the library, the played set and artist scores. */
    override fun pick(
        input: DiscoverInput,
        random: Random,
    ): List<Song> = pick(input.library, input.played, input.artistScores, random)

    /**
     * @param library every song in the library.
     * @param played songs that have any log row, of any age or outcome.
     * @param artistScores global score per main artist; an artist with no entry counts as 0.
     */
    fun pick(
        library: List<Song>,
        played: Set<SongId>,
        artistScores: Map<ArtistId, Double>,
        random: Random,
    ): List<Song> {
        val salt = random.nextLong()
        val ranked =
            library
                .neverPlayed(played)
                .map { Ranked(it, rank(it, artistScores, salt)) }
                .sortedWith(compareByDescending<Ranked> { it.rank }.thenBy { it.song.id.value })
        val best = ranked.map { it.song }.cappedPerArtist(config.maxPerArtist, config.size)
        return best.spreadByArtist(config.artistGap)
    }

    private class Ranked(
        val song: Song,
        val rank: Double,
    )

    private fun rank(
        song: Song,
        artistScores: Map<ArtistId, Double>,
        salt: Long,
    ): Double {
        val taste = tanh((artistScores[song.artistId] ?: 0.0) / config.tasteScale)
        return taste + config.randomWeight * seededUnit(salt, song.id.value)
    }
}
