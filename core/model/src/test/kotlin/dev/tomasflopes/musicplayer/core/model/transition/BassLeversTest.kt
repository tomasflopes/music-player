package dev.tomasflopes.musicplayer.core.model.transition

import dev.tomasflopes.musicplayer.core.model.crossfade.CrossfadeSettings
import dev.tomasflopes.musicplayer.core.model.crossfade.Transition
import dev.tomasflopes.musicplayer.core.model.crossfade.TransitionCause
import dev.tomasflopes.musicplayer.core.model.crossfade.TransitionStyle
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.math.pow

/**
 * The phase 7b levers and the defaults chosen for phone speakers and earbuds (ADR 0109 to 0111). At 120 BPM a beat is
 * 500 ms and a bar 2 s.
 */
class BassLeversTest {
    private val grid120 = BeatGrid(firstBeatMs = 500.0, tempoBpm = 120.0, beatsPerBar = 4, beatsPerPhrase = 16)

    private fun curveFor(
        config: TransitionConfig,
        overlapMs: Long = 12_000,
    ) = BassCurve.forOverlap(overlapMs, grid120, config)

    @Test
    fun `the shipped numbers are the ones of ADR 0109 to 0111`() {
        val config = TransitionConfig()

        assertEquals(6, config.overlapBars)
        assertEquals(3, config.minOverlapBars)
        assertEquals(300, config.bassCutHz)
        assertEquals(-30f, config.bassCutDb)
        assertEquals(1, config.bassSwapBeats)
        assertEquals(1, config.bassOutgoingLeadBeats)
        assertEquals(-4f, config.bassSwapDipDb)
    }

    @Test
    fun `a beat match with the shipped numbers overlaps six bars and swaps on bar three`() {
        val planner = TransitionPlanner(listOf(CrossfadeTechnique(), BeatMatchTechnique()))
        val settings = CrossfadeSettings(enabled = true, durationMs = 4_000, style = TransitionStyle.DJ_MIX)
        val transition =
            Transition(
                transitionSong(1),
                transitionSong(2),
                TransitionCause.END_OF_SONG,
                shuffled = true,
                repeatOne = false,
            )

        val plan =
            assertInstanceOf(
                TransitionPlan.Overlap::class.java,
                planner.plan(settings, transition, transitionProfile(), transitionProfile()),
            )

        val bass = requireNotNull(plan.bass)
        assertEquals(12_000L, plan.overlapMs)
        assertEquals(6_000L, bass.swapStartMs)
        assertEquals(500L, bass.swapMs)
        assertEquals(500L, bass.outgoingLeadMs)
        assertInstanceOf(SwapDipCurve::class.java, plan.curve)
    }

    @Test
    fun `a lead of zero is the old schedule, the outgoing and the incoming swap together`() {
        val together = curveFor(TransitionConfig(bassSwapBeats = 2, bassOutgoingLeadBeats = 0, bassCutDb = -24f))

        for (i in 0..120) {
            val p = i / 120f
            assertEquals(-24f - together.incomingDb(p), together.outgoingDb(p), 1e-4f)
        }
    }

    @Test
    fun `the outgoing bass starts to go the lead before the incoming one opens and both end together`() {
        val curve = curveFor(TransitionConfig(bassSwapBeats = 1, bassOutgoingLeadBeats = 2, bassCutDb = -30f))
        // Swap at 6000 ms for 500 ms; the outgoing cut runs from 5000 ms to 6500 ms.

        assertEquals(0f, curve.outgoingDb(4_900f / 12_000f))
        assertEquals(-30f * (500f / 1_500f), curve.outgoingDb(5_500f / 12_000f), 1e-3f)
        assertEquals(-30f, curve.incomingDb(5_900f / 12_000f))
        assertEquals(-30f, curve.outgoingDb(6_500f / 12_000f), 1e-4f)
        assertEquals(0f, curve.incomingDb(6_500f / 12_000f), 1e-4f)
    }

    @Test
    fun `the lead cannot start before the overlap does`() {
        val curve =
            BassCurve(overlapMs = 12_000, swapStartMs = 1_000, swapMs = 500, cutDb = -30f, outgoingLeadMs = 5_000)

        assertEquals(1_000L, curve.outgoingLeadMs)
        assertEquals(0f, curve.outgoingDb(0f))
    }

    @Test
    fun `the outgoing schedule is linear in dB, so the longer cut has no jump`() {
        val curve = curveFor(TransitionConfig(bassSwapBeats = 1, bassOutgoingLeadBeats = 4, bassCutDb = -30f))
        var previous = curve.outgoingDb(0f)
        var largest = 0f
        for (i in 1..12_000) {
            val now = curve.outgoingDb(i / 12_000f)
            largest = maxOf(largest, abs(now - previous))
            previous = now
        }

        // 30 dB over 2.5 s is 0.012 dB per ms.
        assertEquals(0.012f, largest, 0.002f)
    }

    @Test
    fun `the volume dip falls with the outgoing bass cut and then stays`() {
        val bass = curveFor(TransitionConfig(bassSwapBeats = 1, bassOutgoingLeadBeats = 1))
        val dip = SwapDipCurve(DjVolumeCurve, bass, dipDb = -4f)
        val dipGain = 10f.pow(-4f / 20f)

        assertEquals(1f, dip.outgoing(0f))
        assertEquals(1f, dip.outgoing(5_400f / 12_000f), 1e-6f)
        assertEquals(dipGain * DjVolumeCurve.outgoing(6_500f / 12_000f), dip.outgoing(6_500f / 12_000f), 1e-4f)
        assertEquals(0f, dip.outgoing(1f))
        assertEquals(DjVolumeCurve.incoming(0.3f), dip.incoming(0.3f))
        assertEquals(1f, dip.incoming(1f))
    }

    @Test
    fun `the dip curve is bounded, ends at 1 and 0 and only moves one way`() {
        val bass = curveFor(TransitionConfig(bassSwapBeats = 2, bassOutgoingLeadBeats = 2))
        val dip = SwapDipCurve(DjVolumeCurve, bass, dipDb = -8f)
        var previousOut = 1f
        var previousIn = 0f
        var largestStep = 0f
        for (i in 0..12_000) {
            val p = i / 12_000f
            val out = dip.outgoing(p)
            val inc = dip.incoming(p)
            assertTrue(out in 0f..1f && inc in 0f..1f, "out of range at $p")
            assertTrue(out <= previousOut + 1e-6f && inc >= previousIn - 1e-6f, "not monotone at $p")
            largestStep = maxOf(largestStep, abs(out - previousOut))
            previousOut = out
            previousIn = inc
        }

        assertEquals(1f, dip.outgoing(0f))
        assertEquals(0f, dip.outgoing(1f))
        assertEquals(1f, dip.incoming(1f))
        assertTrue(largestStep < 0.002f, "a step of $largestStep per millisecond could click")
    }

    @Test
    fun `a dip of zero, or a positive one, leaves the base curve alone`() {
        val bass = curveFor(TransitionConfig())

        for (db in listOf(0f, 3f)) {
            val dip = SwapDipCurve(DjVolumeCurve, bass, db)
            for (i in 0..100) {
                assertEquals(DjVolumeCurve.outgoing(i / 100f), dip.outgoing(i / 100f), 1e-6f)
            }
        }
    }

    @Test
    fun `a dip of zero in the config plans the plain DJ volume curve`() {
        val planner =
            TransitionPlanner(listOf(CrossfadeTechnique(), BeatMatchTechnique()), TransitionConfig(bassSwapDipDb = 0f))
        val settings = CrossfadeSettings(enabled = true, durationMs = 4_000, style = TransitionStyle.DJ_MIX)
        val transition =
            Transition(
                transitionSong(1),
                transitionSong(2),
                TransitionCause.END_OF_SONG,
                shuffled = true,
                repeatOne = false,
            )

        val plan = planner.plan(settings, transition, transitionProfile(), transitionProfile())

        assertEquals(DjVolumeCurve, (plan as TransitionPlan.Overlap).curve)
    }
}
