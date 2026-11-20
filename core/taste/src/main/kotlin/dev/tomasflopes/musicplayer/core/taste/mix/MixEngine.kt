package dev.tomasflopes.musicplayer.core.taste.mix

import dev.tomasflopes.musicplayer.core.model.ArtistId
import dev.tomasflopes.musicplayer.core.model.Song
import dev.tomasflopes.musicplayer.core.model.SongId
import dev.tomasflopes.musicplayer.core.model.mixes.MixSeed
import dev.tomasflopes.musicplayer.core.model.sound.SoundSpace
import dev.tomasflopes.musicplayer.core.taste.config.MixConfig
import dev.tomasflopes.musicplayer.core.taste.lists.Candidate
import dev.tomasflopes.musicplayer.core.taste.lists.RadioBatchBuilder
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
 * Seeds with no point in the space give an empty list. Too few candidates give a shorter batch. [refill] builds the
 * next batch of a playing mix the same way, without step 2 (the seed artists were heard when the mix started) and
 * leaving out what is already queued (ADR 0078).
 */
class MixEngine(
    private val config: MixConfig = MixConfig(),
    private val query: MixQuery = AnchorQuery(config),
) {
    private val builder = RadioBatchBuilder(config.builderConfig())

    /**
     * @param library every song in the library; candidates not in it are ignored.
     * @param recentlyPlayed songs played in the last [MixConfig.recentDays] days.
     */
    fun pick(
        seeds: List<MixSeed>,
        library: List<Song>,
        space: SoundSpace,
        recentlyPlayed: Set<SongId>,
        random: Random,
    ): List<Song> = build(seeds, library, space, recentlyPlayed, random, opening = true, emptyList(), emptyList())

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
    ): List<Song> = build(seeds, library, space, recentlyPlayed, random, opening = false, exclude, precedingArtists)

    @Suppress("LongParameterList")
    private fun build(
        seeds: List<MixSeed>,
        library: List<Song>,
        space: SoundSpace,
        recentlyPlayed: Set<SongId>,
        random: Random,
        opening: Boolean,
        exclude: Collection<Song>,
        precedingArtists: List<ArtistId>,
    ): List<Song> {
        val ranking = query.ranking(space, MixAnchors.of(seeds, library, space)) ?: return emptyList()
        val candidates =
            library.mapNotNull { song ->
                val row = space.indexOf(song.id.value)
                if (row < 0) null else Candidate(song, row)
            }
        val first = if (opening) MixOpening.of(seeds, candidates, space, config.openingPerArtist) else emptyList()
        val keyOf = { candidate: Candidate -> ranking.keyOf(candidate.row) }
        return builder.build(first, candidates, keyOf, recentlyPlayed, random, exclude, precedingArtists)
    }
}
