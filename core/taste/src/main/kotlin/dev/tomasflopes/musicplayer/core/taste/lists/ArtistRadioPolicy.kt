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
 * 2. Candidates are every in-library song with a point in the space, the artist's own songs included. The artist is
 *    not special: the same cap of [RadioConfig.maxPerArtist] songs per main artist applies, so at most that many of
 *    the batch are theirs.
 * 3. Ranking, the exploration band, the per-artist cap, duplicate keys and the artist gap are exactly the song radio's
 *    ([RadioBatchBuilder]); there is no seed song, so the batch is [RadioConfig.batchSize] songs and none is fixed
 *    in first place.
 *
 * An artist with no profiled song gives an empty list. Too few candidates give a shorter batch.
 */
class ArtistRadioPolicy(
    config: RadioConfig = RadioConfig(),
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
        return builder.build(emptyList(), candidates, distanceOf, recentlyPlayed, random)
    }
}
