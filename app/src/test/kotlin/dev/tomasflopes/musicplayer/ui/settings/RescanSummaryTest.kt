package dev.tomasflopes.musicplayer.ui.settings

import dev.tomasflopes.musicplayer.core.model.ports.ScanPhase
import dev.tomasflopes.musicplayer.core.model.ports.ScanProgress
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RescanSummaryTest {
    @Test
    fun `an idle library says what the row does`() {
        assertEquals(RESCAN_IDLE, rescanSummary(ScanProgress(ScanPhase.IDLE, 0, 0)))
        assertEquals(RESCAN_IDLE, rescanSummary(ScanProgress(ScanPhase.DONE, 10, 10)))
    }

    @Test
    fun `a running scan shows its progress in the row`() {
        assertEquals("Reading tags 3/10", rescanSummary(ScanProgress(ScanPhase.READING_TAGS, 3, 10)))
    }

    @Test
    fun `a failed scan shows that it failed`() {
        assertEquals("Scan failed", rescanSummary(ScanProgress(ScanPhase.FAILED, 0, 0)))
    }
}
