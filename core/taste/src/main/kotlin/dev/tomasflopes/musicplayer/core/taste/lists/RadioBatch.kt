package dev.tomasflopes.musicplayer.core.taste.lists

import dev.tomasflopes.musicplayer.core.model.ArtistId
import dev.tomasflopes.musicplayer.core.model.Song
import dev.tomasflopes.musicplayer.core.model.SongId
import dev.tomasflopes.musicplayer.core.taste.config.RadioConfig
import kotlin.math.roundToInt
import kotlin.random.Random

// What the song radio (ADR 0072, 0075) and the artist radio (ADR 0075) share once they know their query: how to rank
// candidates, draw exploration from the band, apply the shape rules and spread. Pure Kotlin, internal to this package.

/** A song that may enter a radio batch and its row in the sound space. */
internal class Candidate(
    val song: Song,
    val row: Int,
)

/**
 * Turns candidates into one batch. [initial] songs come first and count towards the batch size and the per-artist cap
 * (the seed song of a song radio; empty for an artist radio).
 */
internal class RadioBatchBuilder(
    private val config: RadioConfig,
) {
    /**
     * @param distanceOf distance of a candidate to the radio's query point (a seed song or an artist centroid).
     * @param recentlyPlayed songs played in the last [RadioConfig.recentDays] days; they rank later but stay.
     */
    fun build(
        initial: List<Song>,
        candidates: List<Candidate>,
        distanceOf: (Candidate) -> Double,
        recentlyPlayed: Set<SongId>,
        random: Random,
    ): List<Song> {
        val wanted = config.batchSize - initial.size
        val explorationWanted = (wanted * config.explorationShare).roundToInt().coerceIn(0, wanted)
        val picker = BatchPicker(initial, config.maxPerArtist)

        val ranked = rank(candidates, distanceOf, recentlyPlayed)
        val nearest = picker.take(ranked, wanted - explorationWanted)
        val salt = random.nextLong()
        val band = ranked.take(config.explorationBand).sortedBy { seededUnit(salt, it.song.id.value) }
        val exploration = picker.take(band, explorationWanted)

        return (initial + interleave(nearest, exploration)).spreadByArtist(config.artistGap)
    }

    private fun rank(
        candidates: List<Candidate>,
        distanceOf: (Candidate) -> Double,
        recentlyPlayed: Set<SongId>,
    ): List<Candidate> =
        candidates
            .map { candidate ->
                val penalty = if (candidate.song.id in recentlyPlayed) config.recentPenalty else 0.0
                candidate to distanceOf(candidate) + penalty
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
}

/** Holds what the batch has so far, so both parts share one per-artist cap and one duplicate rule. */
private class BatchPicker(
    initial: List<Song>,
    private val maxPerArtist: Int,
) {
    private val perArtist = HashMap<ArtistId, Int>()
    private val keys = HashSet<String>()
    private val chosen = HashSet<SongId>()

    init {
        initial.forEach { keep(it) }
    }

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
