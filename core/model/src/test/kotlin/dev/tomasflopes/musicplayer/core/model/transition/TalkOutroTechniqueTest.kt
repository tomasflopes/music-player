package dev.tomasflopes.musicplayer.core.model.transition

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The plan of the talking outro on synthetic profiles (design phase 7, slice 4, ADR 0104). The outgoing song is 180 s
 * long with a body to 160 s and 18 s of quiet tail; the incoming one is 120 BPM with its first beat at 0.5 s.
 */
class TalkOutroTechniqueTest {
    private val technique = TalkOutroTechnique()
    private val on = TransitionConfig(talkEnabled = true)

    private fun plan(
        outgoing: TransitionProfile? = talkOutroProfile(),
        incoming: TransitionProfile? = transitionProfile(),
        config: TransitionConfig = on,
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
    fun `the outgoing song ducks over one bar and fades over the last two bars of the overlap`() {
        val plan = overlap(plan())
        val overlapMs = plan.overlapMs.toFloat()

        assertEquals(0.251f, plan.curve.incoming(500f / overlapMs), 0.003f)
        assertEquals(0.501f, plan.curve.outgoing(2_000f / overlapMs), 0.002f)
        assertEquals(0.501f, plan.curve.outgoing(12_000f / overlapMs), 0.002f)
        assertTrue(plan.curve.outgoing(16_500f / overlapMs) < 0.2f)
        assertEquals(0f, plan.curve.outgoing(1f))
    }

    @Test
    fun `an incoming tempo that is not trusted starts at its music start with a fixed entry and bar`() {
        val unsure = transitionProfile(confidence = 0.05f, firstBeatMs = 900.0, musicStartMs = 600)
        val plan = overlap(plan(incoming = unsure))

        assertEquals(600L, plan.incomingStartMs)
        assertEquals(0.251f, plan.curve.incoming(1_000f / plan.overlapMs), 0.003f)
        assertTrue(plan.curve.incoming(500f / plan.overlapMs) < 0.2f, "a second, not a beat at 120 BPM")
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
        assertNull(plan(config = on.copy(talkLeadMs = 15_000)))
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
    fun `it is on by default`() {
        assertEquals(true, TransitionConfig().talkEnabled)
        assertNotNull(plan(config = TransitionConfig()))
    }

    @Test
    fun `it can be turned off`() {
        assertNull(plan(config = TransitionConfig(talkEnabled = false)))
    }

    @Test
    fun `the numbers come from the config`() {
        val plan = overlap(plan(config = on.copy(talkLeadMs = 2_000, talkDuckDb = -12f, talkFadeBars = 2)))

        assertEquals(162_000L, plan.switchPointMs)
        assertEquals(0.251f, plan.curve.outgoing(3_000f / plan.overlapMs), 0.003f)
        assertEquals(0.251f, plan.curve.outgoing(11_000f / plan.overlapMs), 0.003f, "held until two bars from the end")
        assertTrue(plan.curve.outgoing(14_000f / plan.overlapMs) < 0.2f)
    }

    @Test
    fun `the defaults hold the incoming song at minus twelve decibels and fade over two bars`() {
        val config = TransitionConfig()

        assertEquals(-12f, config.talkHeldDb)
        assertEquals(2, config.talkFadeBars)
        assertEquals(true, config.talkLevelMatch)
        assertEquals(6f, config.talkMaxMatchDb)
    }

    @Test
    fun `the plan holds the incoming song at the held level and raises it only in the final fade`() {
        val plan = overlap(plan())
        val held = 0.251f

        assertEquals(held, plan.curve.incoming(3_000f / plan.overlapMs), 0.003f)
        assertEquals(held, plan.curve.incoming(11_000f / plan.overlapMs), 0.003f, "the hold ends at 13 s")
        assertTrue(plan.curve.incoming(15_000f / plan.overlapMs) > 0.6f)
        assertEquals(1f, plan.curve.incoming(1f))
    }

    @Test
    fun `the held level and the fade length come from the config`() {
        val plan = overlap(plan(config = on.copy(talkHeldDb = -6f, talkFadeBars = 4, talkLevelMatch = false)))

        assertEquals(0.501f, plan.curve.incoming(5_000f / plan.overlapMs), 0.003f)
        assertEquals(0.501f, plan.curve.incoming(8_000f / plan.overlapMs), 0.003f, "the fade is 4 bars, 8 s")
        assertTrue(plan.curve.incoming(12_000f / plan.overlapMs) > 0.7f)
    }

    private fun loudIncoming(body: Float) = transitionProfile(energy = energyCurve(body = body, tail = body))

    @Test
    fun `a louder incoming body lowers the held level by the difference in decibels`() {
        // outgoing body 0.2; incoming body 0.4 is 6.02 dB louder, so the held level drops from -12 to about -18 dB
        val matched = overlap(plan(incoming = loudIncoming(0.4f)))
        val flat = overlap(plan(incoming = loudIncoming(0.4f), config = on.copy(talkLevelMatch = false)))

        assertEquals(0.126f, matched.curve.incoming(5_000f / matched.overlapMs), 0.004f)
        assertEquals(0.251f, flat.curve.incoming(5_000f / flat.overlapMs), 0.004f)
        assertEquals(1f, matched.curve.incoming(1f), "the song still ends at full level")
    }

    @Test
    fun `the loudness match is capped, never raises the level and needs both curves`() {
        val capped = overlap(plan(incoming = loudIncoming(2.0f)))
        val quieter = overlap(plan(incoming = loudIncoming(0.05f)))
        val unknown = overlap(plan(incoming = transitionProfile()))

        assertEquals(0.126f, capped.curve.incoming(5_000f / capped.overlapMs), 0.004f, "at most 6 dB lower")
        assertEquals(0.251f, quieter.curve.incoming(5_000f / quieter.overlapMs), 0.004f, "a quiet song is not raised")
        assertEquals(0.251f, unknown.curve.incoming(5_000f / unknown.overlapMs), 0.004f, "no curve, no match")
    }
}
