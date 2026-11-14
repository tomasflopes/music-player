package dev.tomasflopes.musicplayer.playback.log

import dev.tomasflopes.musicplayer.core.model.SongId
import dev.tomasflopes.musicplayer.core.model.log.EndedBy
import dev.tomasflopes.musicplayer.core.model.log.PlayEvent
import dev.tomasflopes.musicplayer.core.model.log.PlaySource
import dev.tomasflopes.musicplayer.core.model.log.StartedBy
import java.io.File

/**
 * What the checkpoint keeps of the row being played: [event] holds the time listened before the current stretch of
 * playing, and [playingSinceWallMs] is when that stretch began on the wall clock, or null while paused.
 */
data class PendingPlay(
    val event: PlayEvent,
    val playingSinceWallMs: Long?,
)

/**
 * One small, atomically-replaced checkpoint; consumed on the next process start. The recovered row is closed at
 * recovery time: the stretch that was playing counts up to now, capped at the song's duration (ADR 0025).
 */
class PendingPlayStore(
    private val file: File,
    private val wallMs: () -> Long = System::currentTimeMillis,
) {
    fun save(pending: PendingPlay?) {
        if (pending == null) {
            file.delete()
            return
        }
        val event = pending.event
        file.parentFile?.mkdirs()
        File(file.parentFile, "${file.name}.tmp").apply {
            writeText(
                listOf(
                    event.songId.value,
                    event.startedAtEpochMs,
                    event.listenedMs,
                    event.songDurationMs,
                    event.source.wire,
                    event.startedBy.wire,
                    event.shuffle,
                    pending.playingSinceWallMs ?: "",
                ).joinToString("\t"),
            )
            renameTo(file)
        }
    }

    fun takeLeftover(): PlayEvent? {
        val fields = runCatching { file.readText().split('\t') }.getOrNull()
        file.delete()
        return fields?.takeIf { it.size == FIELD_COUNT || it.size == OLD_FIELD_COUNT }?.let(::eventFrom)
    }

    private fun eventFrom(fields: List<String>): PlayEvent? =
        runCatching {
            val duration = fields[DURATION].toLong()
            val running = fields.getOrNull(PLAYING_SINCE)?.toLongOrNull()?.let { (wallMs() - it).coerceAtLeast(0) } ?: 0
            val listened = fields[LISTENED].toLong() + running
            PlayEvent(
                SongId(fields[SONG_ID].toLong()),
                fields[STARTED_AT].toLong(),
                if (duration > 0) minOf(listened, duration) else listened,
                duration,
                EndedBy.STOPPED,
                PlaySource.fromWire(fields[SOURCE]),
                StartedBy.fromWire(fields[STARTED_BY]),
                fields[SHUFFLE].toBooleanStrict(),
            )
        }.getOrNull()

    private companion object {
        const val FIELD_COUNT = 8
        const val OLD_FIELD_COUNT = 7
        const val SONG_ID = 0
        const val STARTED_AT = 1
        const val LISTENED = 2
        const val DURATION = 3
        const val SOURCE = 4
        const val STARTED_BY = 5
        const val SHUFFLE = 6
        const val PLAYING_SINCE = 7
    }
}
