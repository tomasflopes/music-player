package dev.tomasflopes.musicplayer.core.taste.mix

import dev.tomasflopes.musicplayer.core.model.ArtistId
import dev.tomasflopes.musicplayer.core.model.Song
import dev.tomasflopes.musicplayer.core.model.SongId
import dev.tomasflopes.musicplayer.core.model.mixes.MixSeed
import dev.tomasflopes.musicplayer.core.model.sound.SoundSpace
import dev.tomasflopes.musicplayer.core.taste.config.MixConfig
import dev.tomasflopes.musicplayer.core.taste.config.SmartShuffleConfig
import dev.tomasflopes.musicplayer.core.taste.lists.Candidate
import dev.tomasflopes.musicplayer.core.taste.lists.RadioBatchBuilder
import dev.tomasflopes.musicplayer.core.taste.shuffle.BatchOrdering
import kotlin.random.Random

/**
 * Builds one batch for a mix (design §7.6, ADR 0076). Pure Kotlin; it sees the sound only as a [SoundSpace].
 *
 * 1. The seeds become anchor points ([MixAnchors]); the [MixQuery] turns them into a rank key per song.
 * 2. For every artist seed, its [MixConfig.openingPerArtist] own songs closest to its sound open the batch
 *    ([MixOpening]), so the seed artists are always heard.
 * 3. Every in-library song with a point in the space is a candidate, the seeds' own songs included (their key is the
 *    lowest, so a mix starts around what it was made from).
 * 4. The batch is the radio's [RadioBatchBuilder]: [MixConfig.batchSize] songs, [MixConfig.explorationShare] of them
 *    drawn at random from the [MixConfig.explorationBand] nearest, the recent penalty, one song per duplicate key, at
 *    most [MixConfig.maxPerArtist] per main artist and the artist gap.
 *
 * What the mix learned ([MixLearning], ADR 0085) steers all of this and changes nothing else:
 * - liked songs become anchors after the seeds, only if their key against the seed-only anchors is at most
 *   [MixConfig.likedGate], and at most [MixConfig.likedMax] of them (the gate first, then the cap), so a seed edit
 *   drops anchors that no longer sit near the seeds;
 * - banned songs are not candidates, so they are not opening songs either;
 * - a song played in this mix in the cool-down window gets [MixConfig.mixRepeatPenalty] added to its key;
 * - the exploration share of the batch is [MixConfig.explorationShareFor] its informative rows. The builder is made
 *   per batch for that reason; the radio's own builder and numbers are untouched.
 * An empty [MixLearning] gives the batch of slice 2 exactly.
 *
 * The finished batch is then put in play order by the [BatchOrdering] (ADR 0086): the same songs, shuffled with the
 * artist gap, also against [precedingArtists] on a refill, so a mix does not open with the same song every time.
 * [MixConfig.shuffleBatch] false keeps the builder's order (opening songs first, then nearest first).
 *
 * Seeds with no point in the space give an empty list. Too few candidates give a shorter batch. [refill] builds the
 * next batch of a playing mix the same way, without step 2 (the seed artists were heard when the mix started) and
 * leaving out what is already queued (ADR 0078).
 */
class MixEngine(
    private val config: MixConfig = MixConfig(),
    private val query: MixQuery = AnchorQuery(config),
    private val ordering: BatchOrdering = BatchOrdering.smart(SmartShuffleConfig(config.artistGap)),
) {
    /**
     * @param library every song in the library; candidates not in it are ignored.
     * @param recentlyPlayed songs played in the last [MixConfig.recentDays] days.
     */
    @Suppress("LongParameterList")
    fun pick(
        seeds: List<MixSeed>,
        library: List<Song>,
        space: SoundSpace,
        recentlyPlayed: Set<SongId>,
        random: Random,
        learning: MixLearning,
    ): List<Song> =
        build(seeds, library, space, recentlyPlayed, random, learning, opening = true, emptyList(), emptyList())

    /**
     * The next batch of a mix that is playing: no opening songs, nothing in [exclude] (the songs already queued, played
     * ones included) or a copy of it, and the artist gap also holds against [precedingArtists].
     */
    @Suppress("LongParameterList")
    fun refill(
        seeds: List<MixSeed>,
        library: List<Song>,
        space: SoundSpace,
        recentlyPlayed: Set<SongId>,
        random: Random,
        exclude: Collection<Song>,
        precedingArtists: List<ArtistId>,
        learning: MixLearning,
    ): List<Song> =
        build(seeds, library, space, recentlyPlayed, random, learning, opening = false, exclude, precedingArtists)

    @Suppress("LongParameterList")
    private fun build(
        seeds: List<MixSeed>,
        library: List<Song>,
        space: SoundSpace,
        recentlyPlayed: Set<SongId>,
        random: Random,
        learning: MixLearning,
        opening: Boolean,
        exclude: Collection<Song>,
        precedingArtists: List<ArtistId>,
    ): List<Song> {
        val ranking = rankingFor(seeds, library, space, learning.liked) ?: return emptyList()
        val candidates =
            library.mapNotNull { song ->
                val row = space.indexOf(song.id.value)
                if (row < 0 || song.id in learning.banned) null else Candidate(song, row)
            }
        val first = if (opening) MixOpening.of(seeds, candidates, space, config.openingPerArtist) else emptyList()
        val keyOf = { candidate: Candidate ->
            val repeat = if (candidate.song.id in learning.playedInMix) config.mixRepeatPenalty else 0.0
            ranking.keyOf(candidate.row) + repeat
        }
        val builder = RadioBatchBuilder(config.builderConfig(config.explorationShareFor(learning.informativeRows)))
        val batch = builder.build(first, candidates, keyOf, recentlyPlayed, random, exclude, precedingArtists)
        return if (config.shuffleBatch) ordering.order(batch, precedingArtists, random) else batch
    }

    /** The rank key of the seeds' anchors plus the liked ones that pass the gate; null when no seed has a point. */
    private fun rankingFor(
        seeds: List<MixSeed>,
        library: List<Song>,
        space: SoundSpace,
        liked: List<SongId>,
    ): MixRanking? {
        val seedAnchors = MixAnchors.of(seeds, library, space)
        val seedRanking = query.ranking(space, seedAnchors) ?: return null
        val likedAnchors = likedAnchors(liked, library, space, seedRanking)
        return if (likedAnchors.isEmpty()) seedRanking else query.ranking(space, seedAnchors + likedAnchors)
    }

    /** Liked songs within the gate of the seed-only anchors, in the order given, at most [MixConfig.likedMax]. */
    private fun likedAnchors(
        liked: List<SongId>,
        library: List<Song>,
        space: SoundSpace,
        seedRanking: MixRanking,
    ): List<DoubleArray> {
        if (liked.isEmpty() || config.likedMax == 0) return emptyList()
        val inLibrary = library.mapTo(HashSet()) { it.id }
        return liked
            .asSequence()
            .filter { it in inLibrary }
            .map { space.indexOf(it.value) }
            .filter { it >= 0 && seedRanking.keyOf(it) <= config.likedGate }
            .take(config.likedMax)
            .map { MixAnchors.pointOf(space, it) }
            .toList()
    }
}
