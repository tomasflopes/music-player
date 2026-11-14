package dev.tomasflopes.musicplayer.ui.home

import dev.tomasflopes.musicplayer.core.model.ports.ScanPhase
import dev.tomasflopes.musicplayer.core.model.ports.ScanProgress
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ScanStatusTest {
    private fun scan(
        phase: ScanPhase,
        done: Int = 0,
        total: Int = 0,
    ) = ScanProgress(phase, done, total)

    @Test
    fun `each running phase is named, so counts that restart do not look like rescans`() {
        assertEquals("Looking for files…", scan(ScanPhase.LISTING).label())
        assertEquals("Reading tags 3/10", scan(ScanPhase.READING_TAGS, 3, 10).label())
        assertEquals("Saving 4/10", scan(ScanPhase.INDEXING, 4, 10).label())
    }

    @Test
    fun `a failed scan says so`() {
        assertEquals("Scan failed", scan(ScanPhase.FAILED).label())
    }

    @Test
    fun `an idle or finished scan shows nothing on Home`() {
        assertNull(scan(ScanPhase.IDLE).label())
        assertNull(scan(ScanPhase.DONE).label())
    }

    @Test
    fun `only the three working phases count as running`() {
        assertTrue(scan(ScanPhase.LISTING).isRunning())
        assertTrue(scan(ScanPhase.READING_TAGS).isRunning())
        assertTrue(scan(ScanPhase.INDEXING).isRunning())
        assertFalse(scan(ScanPhase.IDLE).isRunning())
        assertFalse(scan(ScanPhase.DONE).isRunning())
        assertFalse(scan(ScanPhase.FAILED).isRunning())
    }
}
