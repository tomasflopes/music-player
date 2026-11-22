package dev.tomasflopes.musicplayer.core.model.transition

import dev.tomasflopes.musicplayer.core.model.Song
import dev.tomasflopes.musicplayer.core.model.crossfade.CrossfadeSettings
import dev.tomasflopes.musicplayer.core.model.crossfade.Transition
import dev.tomasflopes.musicplayer.core.model.crossfade.TransitionCause
import dev.tomasflopes.musicplayer.core.model.crossfade.TransitionStyle
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The planner with synthetic profiles (design phase 7, slice 1). At 120 BPM a beat is 500 ms, a bar 2 s and a phrase
 * 8 s; the first beat is at 0.5 s, so bar and phrase starts fall on 0.5 s plus a multiple of 2 s and 8 s.
 */
class TransitionPlannerTest {
    private val dj = CrossfadeSettings(enabled = true, durationMs = 4_000, style = TransitionStyle.DJ_MIX)
    private val planner = TransitionPlanner(listOf(CrossfadeTechnique(), BeatMatchTechnique()))

    private fun transition(
        outgoing: Song = transitionSong(1),
        incoming: Song = transitionSong(2),
        cause: TransitionCause = TransitionCause.END_OF_SONG,
        shuffled: Boolean = true,
        repeatOne: Boolean = false,
    ) = Transition(outgoing, incoming, cause, shuffled, repeatOne)

    private fun planFor(
        outgoing: TransitionProfile? = transitionProfile(),
        incoming: TransitionProfile? = transitionProfile(),
        settings: CrossfadeSettings = dj,
        transition: Transition = transition(),
        planner: TransitionPlanner = this.planner,
    ) = planner.plan(settings, transition, outgoing, incoming)

    private fun overlap(plan: TransitionPlan) = assertInstanceOf(TransitionPlan.Overlap::class.java, plan)

    @Test
    fun `equal tempo lines up on a phrase start with eight bars`() {
        val plan = overlap(planFor())

        assertEquals("beat-match", plan.technique)
        assertEquals(160_500L, plan.switchPointMs)
        assertEquals(500L, plan.incomingStartMs)
        assertEquals(16_000L, plan.overlapMs)
        assertEquals(1f, plan.incomingSpeed)
        assertTrue(plan.easeBack.isEmpty())
        assertEquals(DjVolumeCurve, plan.curve)
    }

    @Test
    fun `the switch point is on the outgoing grid and the entry on the incoming grid`() {
        val outgoing = transitionProfile(firstBeatMs = 130.0)
        val incoming = transitionProfile(tempo = 121.0, firstBeatMs = 800.0, musicStartMs = 790)

        val plan = overlap(planFor(outgoing = outgoing, incoming = incoming))

        assertEquals(0.0, (plan.switchPointMs - 130.0) % 8_000.0, 1.0)
        val incomingBarMs = 60_000.0 / 121.0 * 4
        val bars = (plan.incomingStartMs - 800.0) / incomingBarMs
        assertEquals(bars.toLong().toDouble(), bars, 1e-3)
    }

    @Test
    fun `a tempo within one and a half percent still matches, with the incoming kept at speed 1`() {
        val plan = overlap(planFor(incoming = transitionProfile(tempo = 121.0)))

        assertEquals(1f, plan.incomingSpeed)
        assertEquals(120.0 / 121.0, plan.ratio, 1e-9)
    }

    @Test
    fun `three percent apart is a plain crossfade in slice 1`() {
        assertEquals(TransitionPlan.Fade(4_000), planFor(incoming = transitionProfile(tempo = 123.6)))
    }

    @Test
    fun `half and double tempo and twelve percent apart are a plain crossfade in slice 1`() {
        assertEquals(TransitionPlan.Fade(4_000), planFor(incoming = transitionProfile(tempo = 60.0)))
        assertEquals(TransitionPlan.Fade(4_000), planFor(incoming = transitionProfile(tempo = 240.0)))
        assertEquals(TransitionPlan.Fade(4_000), planFor(incoming = transitionProfile(tempo = 134.4)))
    }

    @Test
    fun `low confidence on either song is a plain crossfade`() {
        assertEquals(TransitionPlan.Fade(4_000), planFor(incoming = transitionProfile(confidence = 0.4f)))
        assertEquals(TransitionPlan.Fade(4_000), planFor(outgoing = transitionProfile(confidence = 0.49f)))
    }

    @Test
    fun `a missing or unusable profile is a plain crossfade`() {
        assertEquals(TransitionPlan.Fade(4_000), planFor(outgoing = null))
        assertEquals(TransitionPlan.Fade(4_000), planFor(incoming = null))
        assertEquals(TransitionPlan.Fade(4_000), planFor(incoming = transitionProfile(tempo = Double.NaN)))
    }

    @Test
    fun `a short intro shrinks the overlap`() {
        val plan = overlap(planFor(incoming = transitionProfile(introEndMs = 9_000)))

        assertEquals(8_000L, plan.overlapMs)
        assertEquals(168_500L, plan.switchPointMs)
    }

    @Test
    fun `the overlap is capped at twenty seconds`() {
        val slow = transitionProfile(tempo = 90.0, firstBeatMs = 0.0, musicStartMs = 0, outroStartMs = 150_000)

        val plan = overlap(planFor(outgoing = slow, incoming = slow))

        assertTrue(plan.overlapMs <= 20_000L, "overlap ${plan.overlapMs}")
        assertEquals(18_667L, plan.overlapMs)
    }

    @Test
    fun `an intro shorter than four bars is a plain crossfade`() {
        assertEquals(TransitionPlan.Fade(4_000), planFor(incoming = transitionProfile(introEndMs = 4_000)))
    }

    @Test
    fun `a song too short for four bars is a plain crossfade`() {
        val short = transitionProfile(introEndMs = 2_000, outroStartMs = 3_000, musicEndMs = 5_000)

        val result =
            planFor(outgoing = short, transition = transition(outgoing = transitionSong(1, durationMs = 6_000)))

        assertEquals(TransitionPlan.Fade(2_000), result)
    }

    @Test
    fun `when no bar starts in the tail the overlap ends by the music end instead`() {
        val noTail = transitionProfile(outroStartMs = 177_900, musicEndMs = 178_000)

        val plan = overlap(planFor(outgoing = noTail))

        assertTrue(plan.switchPointMs + plan.overlapMs <= 178_000L)
        assertEquals(0.0, (plan.switchPointMs - 500.0) % 2_000.0, 1.0)
    }

    @Test
    fun `the Crossfade style is phase 6 and asks no technique`() {
        val spy = SpyTechnique()

        val crossfade = dj.copy(style = TransitionStyle.CROSSFADE)

        val result = planFor(settings = crossfade, planner = TransitionPlanner(listOf(spy)))

        assertEquals(TransitionPlan.Fade(4_000), result)
        assertEquals(0, spy.calls)
    }

    @Test
    fun `the phase 6 hard-cut rules run before any technique`() {
        val spy = SpyTechnique()
        val spied = TransitionPlanner(listOf(spy, BeatMatchTechnique(), CrossfadeTechnique()))
        val album = transitionSong(1).copy(albumTitle = "Album", albumArtist = "Artist", trackNumber = 1)
        val next = transitionSong(2).copy(albumTitle = "Album", albumArtist = "Artist", trackNumber = 2)
        val hardCuts =
            listOf(
                planFor(settings = dj.copy(enabled = false), planner = spied),
                planFor(transition = transition(repeatOne = true), planner = spied),
                planFor(transition = transition(cause = TransitionCause.TAP_JUMP), planner = spied),
                planFor(transition = transition(cause = TransitionCause.MANUAL_SEEK), planner = spied),
                planFor(transition = transition(outgoing = album, incoming = next, shuffled = false), planner = spied),
                planFor(transition = transition(outgoing = transitionSong(1, durationMs = 0)), planner = spied),
            )

        hardCuts.forEach { assertEquals(TransitionPlan.HardCut, it) }
        assertEquals(0, spy.calls)
    }

    @Test
    fun `techniques are asked in priority order and the first plan wins`() {
        val first = FixedTechnique("first", priority = 1, plan = TransitionPlan.Fade(1_111))
        val second = FixedTechnique("second", priority = 2, plan = TransitionPlan.Fade(2_222))

        assertEquals(TransitionPlan.Fade(1_111), planFor(planner = TransitionPlanner(listOf(second, first))))
    }

    @Test
    fun `an overlap that does not fit the songs is not used`() {
        val broken =
            FixedTechnique(
                "broken",
                priority = 1,
                plan =
                    TransitionPlan.Overlap(
                        technique = "broken",
                        switchPointMs = 179_000,
                        incomingStartMs = 0,
                        overlapMs = 16_000,
                        curve = DjVolumeCurve,
                    ),
            )

        val planner = TransitionPlanner(listOf(broken, CrossfadeTechnique()))

        assertEquals(TransitionPlan.Fade(4_000), planFor(planner = planner))
    }

    @Test
    fun `with no technique registered the phase 6 decision stands`() {
        assertEquals(TransitionPlan.Fade(4_000), planFor(planner = TransitionPlanner(emptyList())))
    }

    private class SpyTechnique : TransitionTechnique {
        var calls = 0
        override val name = "spy"
        override val priority = 0

        override fun plan(request: TransitionRequest): TransitionPlan? {
            calls++
            return null
        }
    }

    private class FixedTechnique(
        override val name: String,
        override val priority: Int,
        private val plan: TransitionPlan,
    ) : TransitionTechnique {
        override fun plan(request: TransitionRequest) = plan
    }
}
