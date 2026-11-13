package dev.tomasflopes.musicplayer.core.model.ports

import dev.tomasflopes.musicplayer.core.model.SongId

/** The featured artists parsed from a song title (§7.11). Read-only; recomputed by every library scan. */
interface SongFeatureReader {
    /** The display names of the artists featured on [songId], in title order. */
    suspend fun namesFor(songId: SongId): List<String>

    /** Every song's featured artists in one query, for rebuilding a search index without N+1 reads. */
    suspend fun all(): Map<SongId, List<String>>
}
