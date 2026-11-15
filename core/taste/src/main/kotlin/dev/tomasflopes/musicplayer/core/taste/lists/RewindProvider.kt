package dev.tomasflopes.musicplayer.core.taste.lists

import dev.tomasflopes.musicplayer.core.model.Song
import dev.tomasflopes.musicplayer.core.model.lists.DynamicListProvider
import dev.tomasflopes.musicplayer.core.model.log.PlaySource
import dev.tomasflopes.musicplayer.core.model.ports.PlayEventSource
import dev.tomasflopes.musicplayer.core.model.ports.PlayedSinceSource
import dev.tomasflopes.musicplayer.core.model.ports.PlaylistAddSource
import dev.tomasflopes.musicplayer.core.model.ports.SearchEventSource
import dev.tomasflopes.musicplayer.core.model.ports.SongReader
import dev.tomasflopes.musicplayer.core.taste.score.TasteSignals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.time.Clock
import kotlin.coroutines.CoroutineContext
import kotlin.random.Random

/** The read-only views of the logs that [RewindProvider] needs, bundled so the provider stays small. */
class RewindLogs(
    val played: PlayedSinceSource,
    val events: PlayEventSource,
    val playlistAdds: PlaylistAddSource,
    val searches: SearchEventSource,
)

/**
 * Songs loved a while ago and not played since (design §7.7, ADR 0064). The work is [RewindPolicy]'s; this class
 * feeds it the library and the logs and re-picks when any of them changes. The cutoff is fixed when collection
 * begins (the logs' lower bound is too, as in `OnDemandScoreBoard`), so a list that stays open does not drift;
 * opening it again re-anchors it. The same [seed] gives the same random part, so the list does not reshuffle while
 * it is open; a new seed (the next app start) gives a different one. Scoring runs on [computeOn] because it reads
 * up to 300 days of log.
 */
class RewindProvider(
    private val songs: SongReader,
    private val logs: RewindLogs,
    private val policy: RewindPolicy,
    private val clock: Clock,
    private val seed: Long,
    private val computeOn: CoroutineContext = Dispatchers.Default,
) : DynamicListProvider {
    override val id = ID
    override val title = "Rewind"
    override val order = 30
    override val playSource = PlaySource.REWIND

    override fun songs(): Flow<List<Song>> =
        flow {
            val now = clock.millis()
            val cutoff = policy.cutoffAt(now)
            val from = policy.logsFrom(now)
            emitAll(
                combine(
                    songs.observeSongs(),
                    logs.played.observePlayedIdsSince(cutoff),
                    logs.events.observeSince(from),
                    logs.playlistAdds.observeSince(from),
                    logs.searches.observeSince(from),
                ) { library, lately, plays, adds, found ->
                    policy.pick(library, lately, TasteSignals(plays, adds, found), now, Random(seed))
                },
            )
        }.flowOn(computeOn).distinctUntilChanged()

    companion object {
        const val ID = "rewind"
    }
}
