package dev.tomasflopes.musicplayer.core.taste.score

import dev.tomasflopes.musicplayer.core.model.SongId
import dev.tomasflopes.musicplayer.core.model.log.EndedBy
import dev.tomasflopes.musicplayer.core.model.log.PlayEvent
import dev.tomasflopes.musicplayer.core.model.log.PlaySource
import dev.tomasflopes.musicplayer.core.model.log.StartedBy
import dev.tomasflopes.musicplayer.core.model.ports.PlayEventSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** A log in memory that re-emits when a row is appended, like Room does, and remembers what it was asked for. */
internal class FakePlayEventSource(
    initial: List<PlayEvent> = emptyList(),
) : PlayEventSource {
    private val log = MutableStateFlow(initial)
    val requestedSince = mutableListOf<Long>()

    fun append(event: PlayEvent) {
        log.value = log.value + event
    }

    override fun observeSince(sinceEpochMs: Long): Flow<List<PlayEvent>> {
        requestedSince += sinceEpochMs
        return log.map { rows -> rows.filter { it.startedAtEpochMs >= sinceEpochMs } }
    }
}

/** A play of [song] that went to the end under shuffle: worth +1. */
internal fun completedPlay(
    song: Long,
    at: Long,
) = PlayEvent(SongId(song), at, 200_000, 200_000, EndedBy.COMPLETED, PlaySource.SHUFFLE_ALL, StartedBy.AUTO, true)
