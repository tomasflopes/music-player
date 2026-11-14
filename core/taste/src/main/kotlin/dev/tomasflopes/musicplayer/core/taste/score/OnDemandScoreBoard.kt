package dev.tomasflopes.musicplayer.core.taste.score

import dev.tomasflopes.musicplayer.core.model.SongId
import dev.tomasflopes.musicplayer.core.model.log.PlayEvent
import dev.tomasflopes.musicplayer.core.model.log.PlaylistAddEvent
import dev.tomasflopes.musicplayer.core.model.log.SearchEvent
import dev.tomasflopes.musicplayer.core.model.log.SearchItemType
import dev.tomasflopes.musicplayer.core.model.ports.PlayEventSource
import dev.tomasflopes.musicplayer.core.model.ports.PlaylistAddSource
import dev.tomasflopes.musicplayer.core.model.ports.SearchEventSource
import dev.tomasflopes.musicplayer.core.model.ports.SongArtistSource
import dev.tomasflopes.musicplayer.core.taste.config.TasteConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import java.time.Clock
import kotlin.coroutines.CoroutineContext

/**
 * Scores the last `horizonDays` of the logs each time they are read or change (ADR 0023, 0049). Nothing is stored,
 * so a changed weight applies at once and there is nothing to rebuild. The scoring runs on [computeOn] because a
 * year of log is tens of thousands of rows and the collector is usually the main thread.
 */
class OnDemandScoreBoard(
    private val events: PlayEventSource,
    private val scorer: TasteScorer,
    private val config: TasteConfig,
    private val clock: Clock,
    private val computeOn: CoroutineContext = Dispatchers.Default,
    private val playlistAdds: PlaylistAddSource = NO_PLAYLIST_ADDS,
    private val searches: SearchEventSource = NO_SEARCHES,
    private val artists: SongArtistSource = NO_ARTISTS,
) : ScoreBoard {
    override fun observeRanked(): Flow<List<SongScore>> = observeScores().map { it.songs }.distinctUntilChanged()

    override fun observeArtistRanked(): Flow<List<ArtistScore>> =
        observeScores().map { it.artists }.distinctUntilChanged()

    private fun observeScores(): Flow<TasteScores> =
        flow {
            val since = clock.millis() - config.decay.horizonMs
            emitAll(
                combine(events.observeSince(since), playlistAdds.observeSince(since), searches.observeSince(since)) {
                    plays,
                    adds,
                    found,
                    ->
                    Logs(plays, adds, found)
                },
            )
        }.map(::rank).flowOn(computeOn)

    private class Logs(
        val plays: List<PlayEvent>,
        val adds: List<PlaylistAddEvent>,
        val searches: List<SearchEvent>,
    )

    /** The query's lower bound was fixed when collection began; the horizon moves with the clock. */
    private suspend fun rank(logs: Logs): TasteScores {
        val now = clock.millis()
        val oldest = now - config.decay.horizonMs
        val signals =
            TasteSignals(
                plays = logs.plays.filter { it.startedAtEpochMs >= oldest },
                playlistAdds = logs.adds.filter { it.atEpochMs >= oldest },
                searches = logs.searches.filter { it.atEpochMs >= oldest },
            )
        val songs: Set<SongId> =
            signals.plays.mapTo(HashSet()) { it.songId } +
                signals.playlistAdds.map { it.songId } +
                signals.searches.filter { it.chosenType == SearchItemType.SONG }.map { SongId(it.chosenId) }
        return scorer.scoreAll(signals, artists.artistsOf(songs), now)
    }

    private companion object {
        val NO_PLAYLIST_ADDS = PlaylistAddSource { kotlinx.coroutines.flow.flowOf(emptyList()) }
        val NO_SEARCHES = SearchEventSource { kotlinx.coroutines.flow.flowOf(emptyList()) }
        val NO_ARTISTS = SongArtistSource { emptyMap() }
    }
}
