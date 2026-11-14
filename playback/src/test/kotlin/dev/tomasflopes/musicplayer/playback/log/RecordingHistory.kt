package dev.tomasflopes.musicplayer.playback.log

import dev.tomasflopes.musicplayer.core.model.ArtistId
import dev.tomasflopes.musicplayer.core.model.SongId
import dev.tomasflopes.musicplayer.core.model.SongRef
import dev.tomasflopes.musicplayer.core.model.log.PlayEvent
import dev.tomasflopes.musicplayer.core.model.log.PlaySource
import dev.tomasflopes.musicplayer.core.model.ports.PlayHistoryWriter
import dev.tomasflopes.musicplayer.playback.queue.QueueEntry

/** Keeps every appended row, in order, so a test can read the log back. */
internal class RecordingHistory : PlayHistoryWriter {
    val events = mutableListOf<PlayEvent>()

    override suspend fun append(event: PlayEvent) {
        events += event
    }
}

internal fun queueEntry(
    id: Long,
    source: PlaySource = PlaySource.SONGS,
    manual: Boolean = false,
) = QueueEntry(SongRef(SongId(id), ArtistId(1)), source, manual)
