package dev.tomasflopes.musicplayer.core.taste.score

import dev.tomasflopes.musicplayer.core.model.ArtistId
import dev.tomasflopes.musicplayer.core.model.log.PlayEvent
import dev.tomasflopes.musicplayer.core.model.log.PlaylistAddEvent
import dev.tomasflopes.musicplayer.core.model.log.SearchEvent

/** One main artist's decayed global score, and when a signal for them last happened (the tie-break). */
data class ArtistScore(
    val artistId: ArtistId,
    val score: Double,
    val lastSignalAtEpochMs: Long,
)

/** Everything the scorer reads: the three append-only logs, over the same stretch of time. */
data class TasteSignals(
    val plays: List<PlayEvent> = emptyList(),
    val playlistAdds: List<PlaylistAddEvent> = emptyList(),
    val searches: List<SearchEvent> = emptyList(),
)

data class TasteScores(
    val songs: List<SongScore>,
    val artists: List<ArtistScore>,
)
