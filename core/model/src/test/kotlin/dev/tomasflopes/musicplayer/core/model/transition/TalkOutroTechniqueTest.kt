package dev.tomasflopes.musicplayer.core.model.transition

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The plan of the talking outro on synthetic profiles (design phase 7, slice 4, ADR 0104). The outgoing song is 180 s
 * long with a body to 160 s and 18 s of quiet tail; the incoming one is 120 BPM with its first beat at 0.5 s.
 */
class TalkOutroTechniqueTest {
    private val technique = TalkOutroTechnique()

    private fun plan(
        outgoing: TransitionProfile? = talkOutroProfile(),
        incoming: TransitionProfile? = transitionProfile(),
        config: TransitionConfig = TransitionConfig(),
        incomingDurationMs: Long = 180_000,
    ) = technique.plan(
        transitionRequest(
            outgoing = outgoing,
            incoming = incoming,
            incomingDurationMs = incomingDurationMs,
            config = config,
        ),
    )

    private fun overlap(plan: TransitionPlan?) = assertInstanceOf(TransitionPlan.Overlap::class.java, plan)

    @Test
    fun `the incoming song starts one second into the outro on its first bar at speed 1`() {
        val plan = overlap(plan())

        assertEquals("talk-outro", plan.technique)
        assertEquals(161_000L, plan.switchPointMs)
        assertEquals(500L, plan.incomingStartMs)
        assertEquals(17_000L, plan.overlapMs)
        assertEquals(1f, plan.incomingSpeed)
        assertEquals(1.0, plan.ratio)
        assertTrue(plan.easeBack.isEmpty())
        assertNull(plan.bass)
        assertNull(plan.outgoingGrid)
        assertTrue(plan.curve is TalkCurve)
    }

    @Test
    fun `the entry is on a bar of the incoming grid`() {
        val incoming = transitionProfile(tempo = 100.0, firstBeatMs = 700.0, musicStartMs = 500)

        val plan = overlap(plan(incoming = incoming))

        assertEquals(700L, plan.incomingStartMs)
        val laterStart = transitionProfile(tempo = 100.0, firstBeatMs = 700.0, musicStartMs = 4_000)
        val later = overlap(plan(incoming = laterStart))
        assertEquals(0.0, (later.incomingStartMs - 700.0) % 2_400.0, 1.0)
        assertTrue(later.incomingStartMs >= 4_000)
    }

    @Test
    fun `the outgoing song ducks over one bar and fades over the last four bars of the overlap`() {
        val plan = overlap(plan())
        val overlapMs = plan.overlapMs.toFloat()

        assertEquals(1f, plan.curve.incoming(500f / overlapMs))
        assertEquals(0.501f, plan.curve.outgoing(2_000f / overlapMs), 0.002f)
        assertEquals(0.501f, plan.curve.outgoing(9_000f / overlapMs), 0.002f)
        assertTrue(plan.curve.outgoing(15_000f / overlapMs) < 0.2f)
        assertEquals(0f, plan.curve.outgoing(1f))
    }

    @Test
    fun `an incoming tempo that is not trusted starts at its music start with a fixed entry and bar`() {
        val unsure = transitionProfile(confidence = 0.05f, firstBeatMs = 900.0, musicStartMs = 600)
        val plan = overlap(plan(incoming = unsure))

        assertEquals(600L, plan.incomingStartMs)
        assertEquals(1f, plan.curve.incoming(1_000f / plan.overlapMs))
        assertTrue(plan.curve.incoming(500f / plan.overlapMs) < 1f, "a second, not a beat at 120 BPM")
        assertEquals(0.501f, plan.curve.outgoing(2_000f / plan.overlapMs), 0.002f)
    }

    @Test
    fun `a long tail is capped at the longest overlap`() {
        val plan = overlap(plan(outgoing = talkOutroProfile(outroStartMs = 100_000, musicEndMs = 170_000)))

        assertEquals(101_000L, plan.switchPointMs)
        assertEquals(20_000L, plan.overlapMs)
    }

    @Test
    fun `the overlap is shortened to what the incoming song has left`() {
        val plan = overlap(plan(incomingDurationMs = 10_500))

        assertEquals(10_000L, plan.overlapMs)
    }

    @Test
    fun `fewer than four seconds of overlap is left to the other techniques`() {
        assertNull(plan(incomingDurationMs = 3_000))
        assertNull(plan(config = TransitionConfig(talkLeadMs = 15_000)))
    }

    @Test
    fun `a quiet musical outro is left alone`() {
        assertNull(plan(outgoing = talkOutroProfile(tail = 0.14f)))
    }

    @Test
    fun `a tail that is too short is left alone`() {
        assertNull(plan(outgoing = talkOutroProfile(outroStartMs = 172_000, musicEndMs = 178_000)))
    }

    @Test
    fun `missing data is left alone`() {
        assertNull(plan(outgoing = null))
        assertNull(plan(incoming = null))
        assertNull(plan(outgoing = transitionProfile()), "a profile without an energy curve")
    }

    @Test
    fun `it can be turned off`() {
        assertNull(plan(config = TransitionConfig(talkEnabled = false)))
    }

    @Test
    fun `the numbers come from the config`() {
        val plan = overlap(plan(config = TransitionConfig(talkLeadMs = 2_000, talkDuckDb = -12f, talkFadeBars = 2)))

        assertEquals(162_000L, plan.switchPointMs)
        assertEquals(0.251f, plan.curve.outgoing(3_000f / plan.overlapMs), 0.003f)
        assertEquals(0.251f, plan.curve.outgoing(11_000f / plan.overlapMs), 0.003f, "held until two bars from the end")
        assertTrue(plan.curve.outgoing(14_000f / plan.overlapMs) < 0.2f)
    }
}
