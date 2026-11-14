package dev.tomasflopes.musicplayer.playback.log

import dev.tomasflopes.musicplayer.core.model.SongId
import dev.tomasflopes.musicplayer.core.model.log.EndedBy
import dev.tomasflopes.musicplayer.core.model.log.PlayEvent
import dev.tomasflopes.musicplayer.core.model.log.PlaySource
import dev.tomasflopes.musicplayer.core.model.log.StartedBy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.nio.file.Files

class PendingPlayStoreTest {
    @Test
    fun `leftover checkpoint is appended once as stopped`() {
        val file = Files.createTempDirectory("pending-play").resolve("event").toFile()
        val event = PlayEvent(SongId(7), 1, 2, 3, EndedBy.SKIPPED, PlaySource.SONGS, StartedBy.PICKED, false)
        PendingPlayStore(file).save(PendingPlay(event, null))

        assertEquals(event.copy(endedBy = EndedBy.STOPPED), PendingPlayStore(file).takeLeftover())
        assertNull(PendingPlayStore(file).takeLeftover())
    }

    @Test
    fun `the playlist of the row survives a checkpoint`() {
        val file = Files.createTempDirectory("pending-play").resolve("event").toFile()
        val event =
            PlayEvent(
                SongId(7),
                1,
                2,
                3,
                EndedBy.SKIPPED,
                PlaySource.PLAYLIST,
                StartedBy.PICKED,
                false,
                playlistId = 12,
            )
        PendingPlayStore(file).save(PendingPlay(event, 5L))

        assertEquals(12L, PendingPlayStore(file).takeLeftover()?.playlistId)
    }

    @Test
    fun `a checkpoint without a playlist reads back without one`() {
        val file = Files.createTempDirectory("pending-play").resolve("event").toFile()
        val event = PlayEvent(SongId(7), 1, 2, 3, EndedBy.SKIPPED, PlaySource.SONGS, StartedBy.PICKED, false)
        PendingPlayStore(file).save(PendingPlay(event, null))

        assertNull(PendingPlayStore(file).takeLeftover()?.playlistId)
    }

    @Test
    fun `a checkpoint written by an earlier build, with 7 or 8 fields, is still read`() {
        val dir = Files.createTempDirectory("pending-play")
        val seven = dir.resolve("seven").toFile().apply { writeText("7\t1\t2\t3\tsongs\tpicked\tfalse") }
        val eight = dir.resolve("eight").toFile().apply { writeText("7\t1\t2\t3\tsongs\tpicked\tfalse\t") }

        listOf(seven, eight).forEach { file ->
            val read = PendingPlayStore(file).takeLeftover()
            assertEquals(SongId(7), read?.songId)
            assertNull(read?.playlistId)
        }
    }
}
