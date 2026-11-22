package dev.tomasflopes.musicplayer.core.taste.lists

import dev.tomasflopes.musicplayer.core.model.ArtistId
import dev.tomasflopes.musicplayer.core.model.Song
import dev.tomasflopes.musicplayer.core.model.SongId
import dev.tomasflopes.musicplayer.core.model.sound.IsolationGuard
import dev.tomasflopes.musicplayer.core.model.sound.SoundNeighbours
import dev.tomasflopes.musicplayer.core.model.sound.SoundSpace
import dev.tomasflopes.musicplayer.core.taste.config.RadioConfig
import kotlin.random.Random

/**
 * Builds one Radio batch from a single seed song (design §7.6, ADR 0072). Pure Kotlin; it sees the sound only as a
 * [SoundSpace].
 *
 * 1. Candidates are the songs of the space that are in the library, minus the seed and every copy of it: the same
 *    file name ([SoundNeighbours.fileKey], ADR 0073) or the same duplicate key (ADR 0070).
 * 2. The nearest part: candidates by distance to the seed's own point, plus [RadioConfig.recentPenalty] when the song
 *    is in `recentlyPlayed`. A recent song ranks later but stays. When the seed is isolated in the sound space
 *    ([IsolationGuard], ADR 0087) a share of these slots goes first to the nearest songs of the seed's main artist
 *    (song rows only, so featured artists are not seen here); the cap and the gap below still apply.
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
    config: RadioConfig = RadioConfig(),
    private val guard: IsolationGuard = IsolationGuard(),
) {
    private val builder = RadioBatchBuilder(config)

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
        val distanceOf = { candidate: Candidate -> space.distance(seedRow, candidate.row) }
        val ownArtist = ownArtist(seed, space, seedRow)
        return builder.build(listOf(seed), candidates, distanceOf, recentlyPlayed, random, ownArtist = ownArtist)
    }

    /**
     * The next batch of a queue that is extending from [seed] (ADR 0078, 0080): the same ranking as [pick] with the
     * seed's own point as the query, but the seed is not in the batch (it is already playing) and nothing in
     * [exclude] (the songs already queued) or a copy of it is chosen. The batch has [RadioConfig.batchSize] songs; the
     * artist gap also holds against [precedingArtists], the artists of the last queued songs.
     */
    @Suppress("LongParameterList")
    fun refill(
        seed: Song,
        library: List<Song>,
        space: SoundSpace,
        recentlyPlayed: Set<SongId>,
        random: Random,
        exclude: Collection<Song>,
        precedingArtists: List<ArtistId>,
    ): List<Song> {
        val seedRow = space.indexOf(seed.id.value)
        if (seedRow < 0) return emptyList()
        val candidates = candidates(seed, library, space)
        val distanceOf = { candidate: Candidate -> space.distance(seedRow, candidate.row) }
        val ownArtist = ownArtist(seed, space, seedRow)
        return builder.build(
            emptyList(),
            candidates,
            distanceOf,
            recentlyPlayed,
            random,
            exclude,
            precedingArtists,
            ownArtist,
        )
    }

    /** Set only when [seed] is isolated in [space]: its own artist's songs get a share of the nearest slots. */
    private fun ownArtist(
        seed: Song,
        space: SoundSpace,
        seedRow: Int,
    ): OwnArtistPart? =
        if (guard.isIsolated(space, seedRow)) {
            OwnArtistPart(guard::artistSlots) { it.song.artistId == seed.artistId }
        } else {
            null
        }

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
}
