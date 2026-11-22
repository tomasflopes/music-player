package dev.tomasflopes.musicplayer.core.model.transition

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class BeatGridTest {
    private val grid = BeatGrid(firstBeatMs = 500.0, tempoBpm = 120.0, beatsPerBar = 4, beatsPerPhrase = 16)

    @Test
    fun `beats, bars and phrases follow the tempo`() {
        assertEquals(500.0, grid.beatMs, 1e-9)
        assertEquals(2_000.0, grid.barMs, 1e-9)
        assertEquals(8_000.0, grid.phraseMs, 1e-9)
    }

    @Test
    fun `the first bar start at or after a time never precedes the first beat`() {
        assertEquals(500.0, grid.barStartAtOrAfter(0.0), 1e-9)
        assertEquals(500.0, grid.barStartAtOrAfter(500.0), 1e-9)
        assertEquals(2_500.0, grid.barStartAtOrAfter(501.0), 1e-9)
        assertEquals(10_500.0, grid.barStartAtOrAfter(10_000.0), 1e-9)
    }

    @Test
    fun `the latest start inside a window`() {
        assertEquals(16_500.0, grid.latestPhraseStart(10_000.0, 20_000.0)!!, 1e-9)
        assertEquals(18_500.0, grid.latestBarStart(10_000.0, 20_000.0)!!, 1e-9)
        assertNull(grid.latestPhraseStart(17_000.0, 20_000.0))
        assertNull(grid.latestBarStart(20_000.0, 10_000.0))
        assertNull(grid.latestBarStart(0.0, 100.0))
    }

    @Test
    fun `the beat offset wraps and is never negative`() {
        assertEquals(0.0, grid.beatOffsetMs(500.0), 1e-9)
        assertEquals(100.0, grid.beatOffsetMs(1_100.0), 1e-9)
        assertEquals(400.0, grid.beatOffsetMs(100.0), 1e-9)
    }

    @Test
    fun `the phase error is signed and wrapped to half a beat`() {
        val other = BeatGrid(0.0, 120.0, 4, 16)

        assertEquals(0.0, BeatPhase.errorMs(grid, 10_500.0, other, 10_000.0), 1e-9)
        assertEquals(30.0, BeatPhase.errorMs(grid, 10_500.0, other, 10_030.0), 1e-9)
        assertEquals(-30.0, BeatPhase.errorMs(grid, 10_500.0, other, 9_970.0), 1e-9)
        assertEquals(-200.0, BeatPhase.errorMs(grid, 10_500.0, other, 10_300.0), 1e-9)
    }
}
