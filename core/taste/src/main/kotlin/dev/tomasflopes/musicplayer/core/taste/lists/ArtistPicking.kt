package dev.tomasflopes.musicplayer.core.taste.lists

import dev.tomasflopes.musicplayer.core.model.ArtistId
import dev.tomasflopes.musicplayer.core.model.Song

// The steps Discover and Rewind share once their songs are ranked (ADR 0063, 0064): a per-artist cap, a spread, and
// a hash that gives each song a stable random part. Pure Kotlin, internal to this package.

/** The first songs of a ranked list, at most [maxPerArtist] per main artist, up to [limit]. The cap is hard. */
internal fun List<Song>.cappedPerArtist(
    maxPerArtist: Int,
    limit: Int,
): List<Song> {
    val perArtist = HashMap<ArtistId, Int>()
    val kept = ArrayList<Song>(minOf(size, limit))
    for (song in this) {
        if (kept.size >= limit) break
        val count = perArtist.getOrDefault(song.artistId, 0)
        if (count < maxPerArtist) {
            perArtist[song.artistId] = count + 1
            kept += song
        }
    }
    return kept
}

/**
 * Keeps the rank order but no main artist repeats within [gap] songs. When every artist left is inside the gap, the
 * one placed longest ago goes first (the gap relaxes).
 */
internal fun List<Song>.spreadByArtist(gap: Int): List<Song> {
    if (gap == 0) return this
    val rest = toMutableList()
    val placed = ArrayList<Song>(rest.size)
    val lastPlace = HashMap<ArtistId, Int>()
    while (rest.isNotEmpty()) {
        val recent = placed.takeLast(gap).mapTo(HashSet()) { it.artistId }
        val free = rest.indexOfFirst { it.artistId !in recent }
        val index = if (free >= 0) free else rest.indices.minBy { lastPlace.getValue(rest[it].artistId) }
        val song = rest.removeAt(index)
        lastPlace[song.artistId] = placed.size
        placed += song
    }
    return placed
}

private const val GOLDEN = -7_046_029_254_386_353_131L
private const val MIX_1 = -4_658_895_280_553_007_687L
private const val MIX_2 = -7_723_592_293_110_705_685L
private const val SHIFT_1 = 30
private const val SHIFT_2 = 27
private const val SHIFT_3 = 31
private const val UNIT_SHIFT = 11
private const val UNIT_SCALE = 1.0 / (1L shl 53)

/** A value in [0, 1) that depends only on [salt] and [id] (a splitmix64 step). */
internal fun seededUnit(
    salt: Long,
    id: Long,
): Double {
    var z = salt + id * GOLDEN
    z = (z xor (z ushr SHIFT_1)) * MIX_1
    z = (z xor (z ushr SHIFT_2)) * MIX_2
    z = z xor (z ushr SHIFT_3)
    return (z ushr UNIT_SHIFT) * UNIT_SCALE
}
