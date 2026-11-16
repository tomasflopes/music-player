package dev.tomasflopes.musicplayer.core.taste.lists

import dev.tomasflopes.musicplayer.core.model.ArtistId
import dev.tomasflopes.musicplayer.core.model.Song
import dev.tomasflopes.musicplayer.core.model.SongId
import dev.tomasflopes.musicplayer.core.model.sound.SoundSpace
import dev.tomasflopes.musicplayer.core.taste.config.RadioConfig
import kotlin.random.Random

/**
 * Builds one Radio batch from an artist (ADR 0075). Pure Kotlin; it sees the sound only as a [SoundSpace].
 *
 * 1. The query is the artist's sound: the mean point ([SoundSpace.centroid]) of the songs whose main artist is the
 *    artist and that have a point in the space. One song is enough; two or more are averaged.
 * 2. The artist's own songs come first: the [RadioConfig.maxPerArtist] closest to that point (one per duplicate key)
 *    open the batch, nearest first, so the first song is always the artist's. They count towards the batch size and
 *    the per-artist cap, so no more than that many of the batch are theirs.
 * 3. The rest is the song radio's ([RadioBatchBuilder]): every other in-library song with a point in the space,
 *    ranked by distance to the point, with the exploration band, duplicate keys, recent penalty and artist gap. The
 *    batch is [RadioConfig.batchSize] songs in all.
 *
 * An artist with no profiled song gives an empty list. Too few candidates give a shorter batch.
 */
class ArtistRadioPolicy(
    private val config: RadioConfig = RadioConfig(),
) {
    private val builder = RadioBatchBuilder(config)

    /**
     * @param library every song in the library; candidates not in it are ignored.
     * @param recentlyPlayed songs played in the last [RadioConfig.recentDays] days.
     */
    fun pick(
        artistId: ArtistId,
        library: List<Song>,
        space: SoundSpace,
        recentlyPlayed: Set<SongId>,
        random: Random,
    ): List<Song> {
        val candidates =
            library.mapNotNull { song ->
                val row = space.indexOf(song.id.value)
                if (row < 0) null else Candidate(song, row)
            }
        val own = candidates.filter { it.song.artistId == artistId }
        if (own.isEmpty()) return emptyList()
        val centre = space.centroid(own.map { it.row })
        val distanceOf = { candidate: Candidate -> space.distanceTo(candidate.row, centre) }
        val opening = own.openingSongs(distanceOf)
        return builder.build(opening, candidates, distanceOf, recentlyPlayed, random)
    }

    /** The artist's songs closest to the point, one per duplicate key, at most [RadioConfig.maxPerArtist]. */
    private fun List<Candidate>.openingSongs(distanceOf: (Candidate) -> Double): List<Song> =
        sortedWith(compareBy<Candidate> { distanceOf(it) }.thenBy { it.song.id.value })
            .distinctBy { it.song.duplicateKey() }
            .take(config.maxPerArtist)
            .map { it.song }
}
