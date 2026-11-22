package dev.tomasflopes.musicplayer.core.taste.stats

import dev.tomasflopes.musicplayer.core.model.SongId
import dev.tomasflopes.musicplayer.core.model.log.EndedBy
import dev.tomasflopes.musicplayer.core.model.log.PlayEvent
import dev.tomasflopes.musicplayer.core.model.log.PlaySource
import dev.tomasflopes.musicplayer.core.model.log.StartedBy
import java.time.Instant
import java.time.ZoneId

internal val UTC: ZoneId = ZoneId.of("UTC")

internal fun epoch(iso: String): Long = Instant.parse(iso).toEpochMilli()

/** One log row; the defaults are a song played to the end under shuffle. */
internal fun row(
    song: Long,
    at: Long,
    listenedMs: Long = 200_000,
    durationMs: Long = 200_000,
    endedBy: EndedBy = EndedBy.COMPLETED,
    source: PlaySource = PlaySource.SHUFFLE_ALL,
) = PlayEvent(SongId(song), at, listenedMs, durationMs, endedBy, source, StartedBy.AUTO, shuffle = true)

/** A skip [listenedMs] into a 200 s song. */
internal fun skip(
    song: Long,
    at: Long,
    listenedMs: Long,
) = row(song, at, listenedMs = listenedMs, endedBy = EndedBy.SKIPPED)
