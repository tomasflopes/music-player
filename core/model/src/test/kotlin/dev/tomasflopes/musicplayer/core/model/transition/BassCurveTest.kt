package dev.tomasflopes.musicplayer.core.model.transition

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * The bass schedule (design phase 7, slice 3). At 120 BPM a beat is 500 ms and a bar 2 s, so an overlap of 8 bars
 * lasts 16 s, its middle bar starts at 8 s and the swap of 2 beats takes 1 s (progress 0.5 to 0.5625).
 */
class BassCurveTest {
    /** The numbers of slice 3 (ADR 0100); the phase 7b defaults are in `BassLeversTest`. */
    private val config =
        TransitionConfig(
            overlapBars = 8,
            bassCutHz = 150,
            bassCutDb = -24f,
            bassSwapBeats = 2,
            bassOutgoingLeadBeats = 0,
            bassSwapDipDb = 0f,
        )
    private val grid120 = BeatGrid(firstBeatMs = 500.0, tempoBpm = 120.0, beatsPerBar = 4, beatsPerPhrase = 16)
    private val curve = BassCurve.forOverlap(overlapMs = 16_000, outgoingGrid = grid120, config = config)

    @Test
    fun `the swap starts on the middle bar and lasts two beats of the outgoing grid`() {
        assertEquals(8_000L, curve.swapStartMs)
        assertEquals(1_000L, curve.swapMs)
        assertEquals(16_000L, curve.overlapMs)
    }

    @Test
    fun `the incoming starts fully cut and the outgoing flat`() {
        assertEquals(config.bassCutDb, curve.incomingDb(0f))
        assertEquals(0f, curve.outgoingDb(0f))
        assertEquals(config.bassCutDb, curve.incomingDb(0.49f))
        assertEquals(0f, curve.outgoingDb(0.49f))
    }

    @Test
    fun `half way through the swap each side is half way`() {
        val half = (8_500f / 16_000f)
        assertEquals(config.bassCutDb / 2, curve.incomingDb(half), 1e-3f)
        assertEquals(config.bassCutDb / 2, curve.outgoingDb(half), 1e-3f)
    }

    @Test
    fun `after the swap the incoming is flat and the outgoing keeps the cut until the end`() {
        assertEquals(0f, curve.incomingDb(9_000f / 16_000f))
        assertEquals(config.bassCutDb, curve.outgoingDb(9_000f / 16_000f))
        assertEquals(0f, curve.incomingDb(1f))
        assertEquals(config.bassCutDb, curve.outgoingDb(1f))
    }

    @Test
    fun `progress outside 0 to 1 and NaN are clamped`() {
        assertEquals(config.bassCutDb, curve.incomingDb(-1f))
        assertEquals(config.bassCutDb, curve.incomingDb(Float.NaN))
        assertEquals(config.bassCutDb, curve.outgoingDb(2f))
    }

    @Test
    fun `the schedule is linear in dB over the swap and has no jump`() {
        var previous = curve.incomingDb(0f)
        var largest = 0f
        for (i in 1..16_000) {
            val now = curve.incomingDb(i / 16_000f)
            largest = maxOf(largest, kotlin.math.abs(now - previous))
            previous = now
        }
        // 24 dB over 1000 ms is 0.024 dB per ms; the schedule is linear in dB and has no jump.
        assertEquals(0.024f, largest, 0.002f)
    }

    @Test
    fun `an odd number of bars swaps on the bar below the half`() {
        val odd = BassCurve.forOverlap(overlapMs = 10_000, outgoingGrid = grid120, config = config)

        assertEquals(4_000L, odd.swapStartMs)
    }

    @Test
    fun `the beat match plan carries the curve for its own overlap and grid`() {
        val planner = TransitionPlanner(listOf(CrossfadeTechnique(), BeatMatchTechnique()), config)
        val settings =
            dev.tomasflopes.musicplayer.core.model.crossfade.CrossfadeSettings(
                enabled = true,
                durationMs = 4_000,
                style = dev.tomasflopes.musicplayer.core.model.crossfade.TransitionStyle.DJ_MIX,
            )
        val transition =
            dev.tomasflopes.musicplayer.core.model.crossfade.Transition(
                transitionSong(1),
                transitionSong(2),
                dev.tomasflopes.musicplayer.core.model.crossfade.TransitionCause.END_OF_SONG,
                shuffled = true,
                repeatOne = false,
            )

        val plan =
            assertInstanceOf(
                TransitionPlan.Overlap::class.java,
                planner.plan(settings, transition, transitionProfile(), transitionProfile()),
            )

        val bass = requireNotNull(plan.bass)
        assertEquals(plan.overlapMs, bass.overlapMs)
        assertEquals(8_000L, bass.swapStartMs)
        assertEquals(1_000L, bass.swapMs)
        assertEquals(config.bassCutDb, bass.incomingDb(0f))
    }

    @Test
    fun `an overlap built without a bass curve has none, as the crossfade and old plans do`() {
        val plan = TransitionPlan.Overlap("x", 0, 0, 1_000, DjVolumeCurve)

        assertNull(plan.bass)
    }
}
