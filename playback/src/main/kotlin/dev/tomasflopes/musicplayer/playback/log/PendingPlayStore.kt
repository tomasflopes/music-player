package dev.tomasflopes.musicplayer.playback.log

import dev.tomasflopes.musicplayer.core.model.SongId
import dev.tomasflopes.musicplayer.core.model.log.EndedBy
import dev.tomasflopes.musicplayer.core.model.log.PlayEvent
import dev.tomasflopes.musicplayer.core.model.log.PlaySource
import dev.tomasflopes.musicplayer.core.model.log.StartedBy
import java.io.File

/** One small, atomically-replaced checkpoint; consumed on the next process start. */
class PendingPlayStore(
    private val file: File,
) {
    fun save(event: PlayEvent?) {
        if (event == null) {
            file.delete()
            return
        }
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
                ).joinToString("\t"),
            )
            renameTo(file)
        }
    }

    fun takeLeftover(): PlayEvent? {
        val fields = runCatching { file.readText().split('\t') }.getOrNull()
        file.delete()
        return fields?.takeIf { it.size == FIELD_COUNT }?.let(::eventFrom)
    }

    private fun eventFrom(fields: List<String>): PlayEvent? =
        runCatching {
            PlayEvent(
                SongId(fields[SONG_ID].toLong()),
                fields[STARTED_AT].toLong(),
                fields[LISTENED].toLong(),
                fields[DURATION].toLong(),
                EndedBy.STOPPED,
                PlaySource.fromWire(fields[SOURCE]),
                StartedBy.fromWire(fields[STARTED_BY]),
                fields[SHUFFLE].toBooleanStrict(),
            )
        }.getOrNull()

    private companion object {
        const val FIELD_COUNT = 7
        const val SONG_ID = 0
        const val STARTED_AT = 1
        const val LISTENED = 2
        const val DURATION = 3
        const val SOURCE = 4
        const val STARTED_BY = 5
        const val SHUFFLE = 6
    }
}
