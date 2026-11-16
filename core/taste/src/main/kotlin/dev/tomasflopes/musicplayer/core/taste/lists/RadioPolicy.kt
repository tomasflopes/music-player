package dev.tomasflopes.musicplayer.core.taste.lists

import dev.tomasflopes.musicplayer.core.model.ArtistId
import dev.tomasflopes.musicplayer.core.model.Song
import dev.tomasflopes.musicplayer.core.model.SongId
import dev.tomasflopes.musicplayer.core.model.sound.SoundNeighbours
import dev.tomasflopes.musicplayer.core.model.sound.SoundSpace
import dev.tomasflopes.musicplayer.core.taste.config.RadioConfig
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Builds one Radio batch from a single seed song (design §7.6, ADR 0072). Pure Kotlin; it sees the sound only as a
 * [SoundSpace].
 *
 * 1. Candidates are the songs of the space that are in the library, minus the seed and every copy of it: the same
 *    file name ([SoundNeighbours.fileKey], ADR 0073) or the same duplicate key (ADR 0070).
 * 2. The nearest part: candidates by distance to the seed's own point, plus [RadioConfig.recentPenalty] when the song
 *    is in `recentlyPlayed`. A recent song ranks later but stays.
 * 3. The exploration part: the [RadioConfig.explorationBand] nearest candidates (same ranking as step 2) in a seeded
 *    random order (a hash of one draw from [Random] and the song id, as in Discover), so one seed gives one batch.
 *    Nothing outside the band is ever drawn (ADR 0075).
 * 4. Both parts obey the same shape rules: at most [RadioConfig.maxPerArtist] songs per main artist (the seed counts)
 *    and no two songs with the same duplicate key.
 * 5. The exploration picks are spread evenly among the nearest ones, then the whole batch is spread so no main artist
 *    repeats within [RadioConfig.artistGap] songs (the gap relaxes when there are too few artists). The seed is first
 *    and nothing can move ahead of it.
 *
 * A seed with no point in the space gives an empty list. Too few candidates give a shorter batch.
 */
class RadioPolicy(
    private val config: RadioConfig = RadioConfig(),
) {
    /**
     * @param seed the song the radio starts from; its own point in [space] is the query.
     * @param library every song in the library; candidates not in it are ignored.
     * @param recentlyPlayed songs played in the last [RadioConfig.recentDays] days.
     */
    fun pick(
        seed: Song,
        library: List<Song>,
        space: SoundSpace,
        recentlyPlayed: Set<SongId>,
        random: Random,
    ): List<Song> {
        val seedRow = space.indexOf(seed.id.value)
        if (seedRow < 0) return emptyList()
        val candidates = candidates(seed, library, space)
        val followers = config.batchSize - 1
        val explorationWanted = (followers * config.explorationShare).roundToInt().coerceIn(0, followers)
        val nearestWanted = followers - explorationWanted

        val picker = Picker(seed, config.maxPerArtist)
        val ranked = nearestRanked(candidates, space, seedRow, recentlyPlayed)
        val nearest = picker.take(ranked, nearestWanted)
        val salt = random.nextLong()
        val band = ranked.take(config.explorationBand).sortedBy { seededUnit(salt, it.song.id.value) }
        val exploration = picker.take(band, explorationWanted)

        return (listOf(seed) + interleave(nearest, exploration)).spreadByArtist(config.artistGap)
    }

    private class Candidate(
        val song: Song,
        val row: Int,
    )

    private fun candidates(
        seed: Song,
        library: List<Song>,
        space: SoundSpace,
    ): List<Candidate> {
        val seedFile = SoundNeighbours.fileKey(seed.fileName)
        val seedKey = seed.duplicateKey()
        return library.mapNotNull { song ->
            val row = space.indexOf(song.id.value)
            val copy =
                song.id == seed.id ||
                    SoundNeighbours.fileKey(song.fileName) == seedFile ||
                    song.duplicateKey() == seedKey
            if (row < 0 || copy) null else Candidate(song, row)
        }
    }

    private fun nearestRanked(
        candidates: List<Candidate>,
        space: SoundSpace,
        seedRow: Int,
        recentlyPlayed: Set<SongId>,
    ): List<Candidate> =
        candidates
            .map { candidate ->
                val penalty = if (candidate.song.id in recentlyPlayed) config.recentPenalty else 0.0
                candidate to space.distance(seedRow, candidate.row) + penalty
            }.sortedWith(compareBy<Pair<Candidate, Double>> { it.second }.thenBy { it.first.song.id.value })
            .map { it.first }

    /** Puts the exploration songs at even places among the nearest ones, keeping the order inside each part. */
    private fun interleave(
        nearest: List<Song>,
        exploration: List<Song>,
    ): List<Song> {
        val total = nearest.size + exploration.size
        val merged = ArrayList<Song>(total)
        var nearIndex = 0
        var exploreIndex = 0
        for (place in 1..total) {
            val due = exploreIndex < exploration.size && place * exploration.size >= (exploreIndex + 1) * total
            merged += if (due || nearIndex >= nearest.size) exploration[exploreIndex++] else nearest[nearIndex++]
        }
        return merged
    }

    /** Holds what the batch has so far, so both parts share one per-artist cap and one duplicate rule. */
    private class Picker(
        seed: Song,
        private val maxPerArtist: Int,
    ) {
        private val perArtist = HashMap<ArtistId, Int>().apply { put(seed.artistId, 1) }
        private val keys = HashSet<String>().apply { add(seed.duplicateKey()) }
        private val chosen = HashSet<SongId>().apply { add(seed.id) }

        /** The first [limit] songs of [ranked] that fit, in that order. */
        fun take(
            ranked: List<Candidate>,
            limit: Int,
        ): List<Song> {
            val taken = ArrayList<Song>(limit)
            for (candidate in ranked) {
                if (taken.size >= limit) break
                if (fits(candidate.song)) {
                    keep(candidate.song)
                    taken += candidate.song
                }
            }
            return taken
        }

        private fun fits(song: Song): Boolean =
            song.id !in chosen &&
                perArtist.getOrDefault(song.artistId, 0) < maxPerArtist &&
                song.duplicateKey() !in keys

        private fun keep(song: Song) {
            chosen += song.id
            keys += song.duplicateKey()
            perArtist[song.artistId] = perArtist.getOrDefault(song.artistId, 0) + 1
        }
    }
}
