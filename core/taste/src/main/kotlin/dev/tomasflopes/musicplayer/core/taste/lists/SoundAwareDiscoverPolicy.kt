package dev.tomasflopes.musicplayer.core.taste.lists

import dev.tomasflopes.musicplayer.core.model.ArtistId
import dev.tomasflopes.musicplayer.core.model.Song
import dev.tomasflopes.musicplayer.core.model.SongId
import dev.tomasflopes.musicplayer.core.model.sound.SoundSpace
import dev.tomasflopes.musicplayer.core.taste.config.DiscoverConfig
import dev.tomasflopes.musicplayer.core.taste.config.MixConfig
import dev.tomasflopes.musicplayer.core.taste.mix.AnchorQuery
import dev.tomasflopes.musicplayer.core.taste.mix.MixQuery
import dev.tomasflopes.musicplayer.core.taste.mix.MixRanking
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.tanh
import kotlin.random.Random

/**
 * Discover that also listens to the sound (ADR 0071). Pure Kotlin. Steps 1, 3 and 4 are those of [DiscoverPolicy].
 *
 * 1. Candidates: never played, a copy of a recording counted once (ADR 0070).
 * 2. Rank = [DiscoverConfig.artistWeight] x the artist's squashed taste score + [DiscoverConfig.soundWeight] x sound
 *    term + [DiscoverConfig.soundRandomWeight] x the seeded random part (same hash as [DiscoverPolicy]).
 *    The sound term comes from the [MixQuery] key (ADR 0076: distance to the nearest anchor over that anchor's reach)
 *    as `exp(-key) - exp(-neutralKey)`: 0 for a song as far out as an anchor's reach, positive nearer, negative
 *    farther. A song with no sound profile gets 0, so it ranks by artist alone and is not buried.
 * 3. [DiscoverConfig.explorationShare] of the list is exploration: drawn at random, seeded, only from the
 *    [DiscoverConfig.explorationBand] candidates nearest to an anchor in sound (as the radio does, ADR 0075), so the
 *    list does not collapse onto the sound the artist scores favour. The rest is the best ranked. One per-artist cap
 *    holds for both, and exploration slots the best ranked cannot fill go back to them.
 * 4. The two parts are interleaved and spread so no main artist repeats within the gap (relaxed when too few).
 *
 * With no anchors (nothing liked has a profile) or no sound space, the list is exactly [fallback]'s.
 */
class SoundAwareDiscoverPolicy(
    private val config: DiscoverConfig = DiscoverConfig(),
    private val fallback: DiscoverRule = DiscoverPolicy(config),
    private val query: MixQuery = AnchorQuery(MixConfig(densityRank = config.densityRank)),
) : DiscoverRule {
    private val neutralAffinity = exp(-config.neutralKey)

    override fun pick(
        input: DiscoverInput,
        random: Random,
    ): List<Song> {
        val space = input.space
        val anchors = space?.let { DiscoverAnchors.choose(input.songScores, input.library, it, config) }.orEmpty()
        val ranking = space?.let { query.ranking(it, anchors) }
        return if (space == null || ranking == null) {
            fallback.pick(input, random)
        } else {
            soundAware(input, space, ranking, random)
        }
    }

    private fun soundAware(
        input: DiscoverInput,
        space: SoundSpace,
        ranking: MixRanking,
        random: Random,
    ): List<Song> {
        val salt = random.nextLong()
        val scored =
            input.library.neverPlayed(input.played).map { song ->
                val row = space.indexOf(song.id.value)
                val key = if (row >= 0) ranking.keyOf(row) else null
                Scored(song, key, rank(song, key, input.artistScores, salt))
            }
        val ranked = scored.sortedWith(compareByDescending<Scored> { it.rank }.thenBy { it.song.id.value })
        return assemble(ranked, salt).spreadByArtist(config.artistGap)
    }

    private class Scored(
        val song: Song,
        /** Rank key of the sound query, or `null` when the song has no point in the space. */
        val key: Double?,
        val rank: Double,
    )

    private fun rank(
        song: Song,
        key: Double?,
        artistScores: Map<ArtistId, Double>,
        salt: Long,
    ): Double {
        val taste = tanh((artistScores[song.artistId] ?: 0.0) / config.tasteScale)
        val sound = if (key == null) 0.0 else exp(-key) - neutralAffinity
        return config.artistWeight * taste + config.soundWeight * sound +
            config.soundRandomWeight * seededUnit(salt, song.id.value)
    }

    private fun assemble(
        ranked: List<Scored>,
        salt: Long,
    ): List<Song> {
        val picker = CapPicker(config.maxPerArtist)
        val exploreWanted = (config.size * config.explorationShare).roundToInt().coerceIn(0, config.size)
        val best = picker.take(ranked, config.size - exploreWanted)
        val band =
            ranked
                .filter { it.key != null }
                .sortedWith(compareBy<Scored> { it.key }.thenBy { it.song.id.value })
                .take(config.explorationBand)
                .sortedBy { seededUnit(salt xor EXPLORATION_SALT, it.song.id.value) }
        val exploration = picker.take(band, exploreWanted)
        val topUp = picker.take(ranked, config.size - best.size - exploration.size)
        return interleaveEvenly(best + topUp, exploration)
    }

    /** Holds the songs chosen so far, so both parts share one per-artist cap. */
    private class CapPicker(
        private val maxPerArtist: Int,
    ) {
        private val perArtist = HashMap<ArtistId, Int>()
        private val chosen = HashSet<SongId>()

        fun take(
            from: List<Scored>,
            limit: Int,
        ): List<Song> {
            val taken = ArrayList<Song>()
            for (candidate in from) {
                if (taken.size >= limit) break
                if (fits(candidate.song)) {
                    keep(candidate.song)
                    taken += candidate.song
                }
            }
            return taken
        }

        private fun fits(song: Song): Boolean =
            song.id !in chosen && perArtist.getOrDefault(song.artistId, 0) < maxPerArtist

        private fun keep(song: Song) {
            chosen += song.id
            perArtist[song.artistId] = perArtist.getOrDefault(song.artistId, 0) + 1
        }
    }

    private companion object {
        /** Keeps the exploration draw independent of the random part of the rank, which uses the same salt. */
        const val EXPLORATION_SALT = 0x5DEECE66DL
    }
}
