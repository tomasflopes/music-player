package dev.tomasflopes.musicplayer.core.taste.lists

import dev.tomasflopes.musicplayer.core.model.Song
import dev.tomasflopes.musicplayer.core.model.SongId
import java.text.Normalizer
import java.util.Locale

// The duplicate rule of ADR 0070. The library holds the same recording under two file names (HH/Chill and
// HH/Dillaz are examples), so "never played" has to look past the song id. Pure Kotlin, internal to this package.

private val MARKS = Regex("\\p{M}+")
private val NOT_TEXT = Regex("[^\\p{L}\\p{N}\\s]+")
private val SPACES = Regex("\\s+")
private const val SEPARATOR = '\u0000'

private fun normalised(text: String): String =
    Normalizer
        .normalize(text, Normalizer.Form.NFD)
        .replace(MARKS, "")
        .lowercase(Locale.ROOT)
        .replace(NOT_TEXT, "")
        .replace(SPACES, " ")
        .trim()

/** Normalised main artist and title: lower case, accents and punctuation gone, spaces collapsed. */
internal fun Song.duplicateKey(): String = normalised(tagArtistName) + SEPARATOR + normalised(title)

/**
 * The songs Discover may pick from: not played, and not a copy of a played song. A song counts as played when any
 * copy with the same key is in [played]. Of the copies that are left, only the one with the lowest song id stays.
 * Ordered by song id.
 */
internal fun List<Song>.neverPlayed(played: Set<SongId>): List<Song> {
    val playedKeys = filter { it.id in played }.mapTo(HashSet()) { it.duplicateKey() }
    return filter { it.id !in played && it.duplicateKey() !in playedKeys }
        .groupBy { it.duplicateKey() }
        .values
        .map { copies -> copies.minBy { it.id.value } }
        .sortedBy { it.id.value }
}
