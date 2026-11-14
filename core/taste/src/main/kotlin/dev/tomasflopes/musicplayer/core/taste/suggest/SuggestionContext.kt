package dev.tomasflopes.musicplayer.core.taste.suggest

import dev.tomasflopes.musicplayer.core.model.AlbumId
import dev.tomasflopes.musicplayer.core.model.ArtistId
import dev.tomasflopes.musicplayer.core.model.Song
import dev.tomasflopes.musicplayer.core.model.SongArtists
import dev.tomasflopes.musicplayer.core.model.SongId
import kotlin.math.ln

/**
 * What the filters know about one playlist at one moment: its songs, the songs dismissed for it, who the artists of
 * each song are, and the taste scores. Everything a filter counts is worked out once here, not once per candidate.
 */
class SuggestionContext(
    /** The playlist's songs that are in the library. */
    val members: List<Song>,
    /** Songs dismissed for this playlist that are in the library. */
    val dismissed: List<Song>,
    private val artists: Map<SongId, SongArtists> = emptyMap(),
    private val songScores: Map<SongId, Double> = emptyMap(),
    private val artistScores: Map<ArtistId, Double> = emptyMap(),
) {
    /** How many playlist songs have this main artist. */
    val memberMainArtists: Map<ArtistId, Int> = members.groupingBy { it.artistId }.eachCount()

    /** How many playlist songs have this artist as main or featured artist. */
    val memberArtists: Map<ArtistId, Int> = members.flatMap(::artistIdsOf).groupingBy { it }.eachCount()

    /** How many playlist songs are on this album. */
    val memberAlbums: Map<AlbumId, Int> = members.mapNotNull { it.albumId }.groupingBy { it }.eachCount()

    /** How many dismissed songs have this main artist. */
    val dismissedMainArtists: Map<ArtistId, Int> = dismissed.groupingBy { it.artistId }.eachCount()

    /** How many dismissed songs are on this album. */
    val dismissedAlbums: Map<AlbumId, Int> = dismissed.mapNotNull { it.albumId }.groupingBy { it }.eachCount()

    /** The main artist, then the featured artists that are main artists in the library (each once). */
    fun artistIdsOf(song: Song): List<ArtistId> {
        val known = artists[song.id]
        return (listOf(known?.main ?: song.artistId) + known?.featured.orEmpty()).distinct()
    }

    /** The global score of the song; 0 when it has none. */
    fun songScore(id: SongId): Double = songScores[id] ?: 0.0

    /** The global score of the main artist; 0 when it has none. */
    fun artistScore(id: ArtistId): Double = artistScores[id] ?: 0.0
}

/** More playlist songs behind a link make it stronger, with diminishing returns: 1 for one song, then slowly up. */
internal fun repeatBoost(count: Int): Double = if (count <= 0) 0.0 else 1.0 + ln(count.toDouble())
