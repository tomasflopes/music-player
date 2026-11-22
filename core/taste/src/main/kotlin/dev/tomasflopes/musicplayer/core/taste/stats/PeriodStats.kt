package dev.tomasflopes.musicplayer.core.taste.stats

import dev.tomasflopes.musicplayer.core.model.stats.AlbumPlays
import dev.tomasflopes.musicplayer.core.model.stats.ArtistPlays
import dev.tomasflopes.musicplayer.core.model.stats.FirstPlay
import dev.tomasflopes.musicplayer.core.model.stats.SongPlays

/** Songs heard for the first time ever in the period: how many, and the earliest few. */
data class NewDiscoveries(
    val count: Int,
    val first: List<FirstPlay>,
)

/** Everything the Wrapped cards of one period show, as ids and numbers (names and covers are the screen's job). */
data class PeriodStats(
    val period: StatsPeriod,
    val totalPlays: Int,
    val topSongs: List<SongPlays>,
    val topArtists: List<ArtistPlays>,
    val topAlbums: List<AlbumPlays>,
    val listenedMs: Long,
    val newDiscoveries: NewDiscoveries,
    val mostSkipped: List<SongSkips>,
    val clock: ListeningClock,
    val comeback: Comeback?,
) {
    val isEmpty: Boolean get() = totalPlays == 0

    val minutesListened: Long get() = listenedMs / MS_PER_MINUTE

    companion object {
        private const val MS_PER_MINUTE = 60_000L

        fun empty(period: StatsPeriod) =
            PeriodStats(
                period = period,
                totalPlays = 0,
                topSongs = emptyList(),
                topArtists = emptyList(),
                topAlbums = emptyList(),
                listenedMs = 0,
                newDiscoveries = NewDiscoveries(0, emptyList()),
                mostSkipped = emptyList(),
                clock = ListeningClock.EMPTY,
                comeback = null,
            )
    }
}
