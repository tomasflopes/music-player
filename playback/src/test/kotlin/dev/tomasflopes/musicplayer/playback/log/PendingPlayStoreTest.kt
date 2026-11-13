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
        PendingPlayStore(file).save(event)

        assertEquals(event.copy(endedBy = EndedBy.STOPPED), PendingPlayStore(file).takeLeftover())
        assertNull(PendingPlayStore(file).takeLeftover())
    }
}
