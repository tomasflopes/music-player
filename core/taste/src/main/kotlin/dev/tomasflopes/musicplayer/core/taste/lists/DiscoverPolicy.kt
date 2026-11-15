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
 * 1. Candidates are the songs with no `play_event` row at all.
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
) {
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
                .filter { it.id !in played }
                .map { Ranked(it, rank(it, artistScores, salt)) }
                .sortedWith(compareByDescending<Ranked> { it.rank }.thenBy { it.song.id.value })
        return spread(capped(ranked))
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
        return taste + config.randomWeight * unit(salt, song.id.value)
    }

    private fun capped(ranked: List<Ranked>): List<Song> {
        val perArtist = HashMap<ArtistId, Int>()
        val kept = ArrayList<Song>(minOf(ranked.size, config.size))
        for (entry in ranked) {
            if (kept.size >= config.size) break
            val count = perArtist.getOrDefault(entry.song.artistId, 0)
            if (count < config.maxPerArtist) {
                perArtist[entry.song.artistId] = count + 1
                kept += entry.song
            }
        }
        return kept
    }

    private fun spread(ranked: List<Song>): List<Song> {
        if (config.artistGap == 0) return ranked
        val rest = ranked.toMutableList()
        val placed = ArrayList<Song>(rest.size)
        val lastPlace = HashMap<ArtistId, Int>()
        while (rest.isNotEmpty()) {
            val recent = placed.takeLast(config.artistGap).mapTo(HashSet()) { it.artistId }
            val free = rest.indexOfFirst { it.artistId !in recent }
            val index = if (free >= 0) free else rest.indices.minBy { lastPlace.getValue(rest[it].artistId) }
            val song = rest.removeAt(index)
            lastPlace[song.artistId] = placed.size
            placed += song
        }
        return placed
    }

    private companion object {
        const val GOLDEN = -7_046_029_254_386_353_131L
        const val MIX_1 = -4_658_895_280_553_007_687L
        const val MIX_2 = -7_723_592_293_110_705_685L
        const val SHIFT_1 = 30
        const val SHIFT_2 = 27
        const val SHIFT_3 = 31
        const val UNIT_SHIFT = 11
        const val UNIT_SCALE = 1.0 / (1L shl 53)

        /** A value in [0, 1) that depends only on [salt] and [id] (a splitmix64 step). */
        fun unit(
            salt: Long,
            id: Long,
        ): Double {
            var z = salt + id * GOLDEN
            z = (z xor (z ushr SHIFT_1)) * MIX_1
            z = (z xor (z ushr SHIFT_2)) * MIX_2
            z = z xor (z ushr SHIFT_3)
            return (z ushr UNIT_SHIFT) * UNIT_SCALE
        }
    }
}
