package dev.tomasflopes.musicplayer.core.model.ports

import dev.tomasflopes.musicplayer.core.model.SongId

/** The featured artists parsed from a song title (§7.11). Read-only; recomputed by every library scan. */
interface SongFeatureReader {
    /** The display names of the artists featured on [songId], in title order. */
    suspend fun namesFor(songId: SongId): List<String>
}
