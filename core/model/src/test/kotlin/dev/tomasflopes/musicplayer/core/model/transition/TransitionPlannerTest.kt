package dev.tomasflopes.musicplayer.core.model.transition

import dev.tomasflopes.musicplayer.core.model.AlbumId
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
 * The planner with synthetic profiles (design phase 7, slices 1 and 2). At 120 BPM a beat is 500 ms,
 * a bar 2 s and a phrase 8 s; the first beat is at 0.5 s, so bar and phrase starts fall on 0.5 s
 * plus a multiple of 2 s and 8 s.
 */
class TransitionPlannerTest {
    private val dj = CrossfadeSettings(enabled = true, durationMs = 4_000, style = TransitionStyle.DJ_MIX)

    /** The numbers of slices 1 to 4 (8 bars, no volume move on the swap); the shipped defaults are tested apart. */
    private val slice4 = TransitionConfig(overlapBars = 8, bassSwapDipDb = 0f)
    private val planner = TransitionPlanner(listOf(CrossfadeTechnique(), BeatMatchTechnique()), slice4)
    private val storedRulePlanner =
        TransitionPlanner(
            listOf(CrossfadeTechnique(), BeatMatchTechnique(StoredConfidenceReliability())),
            slice4,
        )
    private val shipped = TransitionPlanner(listOf(CrossfadeTechnique(), BeatMatchTechnique()))

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
    fun `a tempo one percent apart matches by stretching the incoming song to the outgoing tempo`() {
        val plan = overlap(planFor(incoming = transitionProfile(tempo = 121.0)))

        assertEquals(120.0 / 121.0, plan.ratio, 1e-9)
        assertEquals((120.0 / 121.0).toFloat(), plan.incomingSpeed)
        assertEquals(4, plan.easeBack.size)
        assertEquals(1f, plan.easeBack.last().speed)
    }

    @Test
    fun `three percent apart matches at the stretched speed and eases back one step on each bar`() {
        val plan = overlap(planFor(incoming = transitionProfile(tempo = 123.6)))

        assertEquals(120.0 / 123.6, plan.ratio, 1e-9)
        assertEquals(
            (plan.incomingStartMs + plan.incomingSpanMs).toDouble(),
            plan.easeBack
                .first()
                .atMs
                .toDouble(),
            1.5,
        )
        val incomingBarMs = 60_000.0 / 123.6 * 4
        plan.easeBack.zipWithNext().forEach { (a, b) ->
            assertEquals(incomingBarMs, (b.atMs - a.atMs).toDouble(), 1.5)
            assertTrue(b.speed - a.speed <= 0.01f + 1e-6f, "step ${b.speed - a.speed}")
            assertTrue(b.speed > a.speed)
        }
        assertTrue(plan.easeBack.first().speed > plan.incomingSpeed)
        assertEquals(1f, plan.easeBack.last().speed)
    }

    @Test
    fun `the overlap is eight bars of real time and the incoming song covers speed times that`() {
        val plan = overlap(planFor(incoming = transitionProfile(tempo = 123.6)))

        assertEquals(16_000L, plan.overlapMs)
        assertEquals(16_000 * (120.0 / 123.6), plan.incomingSpanMs.toDouble(), 1.0)
    }

    @Test
    fun `half and double tempo match at the reading that needs the least stretch`() {
        val half = overlap(planFor(incoming = transitionProfile(tempo = 60.0)))
        val double = overlap(planFor(incoming = transitionProfile(tempo = 240.0)))
        val stretchedHalf =
            overlap(planFor(outgoing = transitionProfile(tempo = 72.0), incoming = transitionProfile(tempo = 140.0)))

        assertEquals(1f, half.incomingSpeed)
        assertEquals(120.0, half.incomingGrid!!.tempoBpm, 1e-9)
        assertTrue(half.easeBack.isEmpty())
        assertEquals(1f, double.incomingSpeed)
        assertEquals(120.0, double.incomingGrid!!.tempoBpm, 1e-9)
        assertEquals(72.0 / 70.0, stretchedHalf.ratio, 1e-9)
        assertEquals(70.0, stretchedHalf.incomingGrid!!.tempoBpm, 1e-9)
    }

    @Test
    fun `the plain reading wins a tie over half and double`() {
        val plan = overlap(planFor(incoming = transitionProfile(tempo = 120.0)))

        assertEquals(120.0, plan.incomingGrid!!.tempoBpm, 1e-9)
    }

    @Test
    fun `eight percent apart still matches and eight and a half does not`() {
        val limit =
            overlap(planFor(outgoing = transitionProfile(tempo = 108.0), incoming = transitionProfile(tempo = 100.0)))
        val over = planFor(outgoing = transitionProfile(tempo = 108.5), incoming = transitionProfile(tempo = 100.0))

        assertEquals(1.08, limit.ratio, 1e-9)
        assertEquals(TransitionPlan.Fade(4_000), over)
    }

    @Test
    fun `twelve percent apart is a plain crossfade, even at half or double`() {
        assertEquals(TransitionPlan.Fade(4_000), planFor(incoming = transitionProfile(tempo = 134.4)))
        assertEquals(TransitionPlan.Fade(4_000), planFor(incoming = transitionProfile(tempo = 107.1)))
    }

    @Test
    fun `the incoming song has to cover its stretched share of the overlap inside its intro`() {
        val faster = transitionProfile(tempo = 120.0, introEndMs = 16_300)
        val outgoing = transitionProfile(tempo = 125.0)

        val plan = overlap(planFor(outgoing = outgoing, incoming = faster))

        assertTrue(plan.incomingStartMs + plan.incomingSpanMs <= 16_300L, "span ${plan.incomingSpanMs}")
    }

    @Test
    fun `the stored rule of slices 1 and 2 still needs a confidence of 0_5 on both songs`() {
        val fade = TransitionPlan.Fade(4_000)

        assertEquals(fade, planFor(planner = storedRulePlanner, incoming = transitionProfile(confidence = 0.4f)))
        assertEquals(fade, planFor(planner = storedRulePlanner, outgoing = transitionProfile(confidence = 0.49f)))
        overlap(planFor(planner = storedRulePlanner, incoming = transitionProfile(confidence = 0.5f)))
    }

    @Test
    fun `the calibrated rule matches at 0_15 on both songs and is a plain crossfade just below`() {
        val fade = TransitionPlan.Fade(4_000)
        val below = transitionProfile(confidence = 0.149f)
        val at = transitionProfile(confidence = 0.15f)
        val above = transitionProfile(confidence = 0.151f)

        assertEquals(fade, planFor(incoming = below))
        assertEquals(fade, planFor(outgoing = below))
        assertEquals(fade, planFor(outgoing = below, incoming = below))
        overlap(planFor(outgoing = at, incoming = at))
        overlap(planFor(outgoing = above, incoming = above))
    }

    @Test
    fun `songs between 0_15 and 0_5 are beat matched by the calibrated rule and not by the stored one`() {
        val middling = transitionProfile(confidence = 0.2f)

        overlap(planFor(outgoing = middling, incoming = middling))
        assertEquals(
            TransitionPlan.Fade(4_000),
            planFor(planner = storedRulePlanner, outgoing = middling, incoming = middling),
        )
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
    fun `an intro that holds three bars but not four still beat matches, as Mo Boy into 1993 does`() {
        val moBoy =
            transitionProfile(
                tempo = 90.0,
                firstBeatMs = 730.0,
                confidence = 0.42f,
                musicStartMs = 100,
                introEndMs = 57_500,
                outroStartMs = 269_900,
                musicEndMs = 277_000,
            )
        val matue1993 =
            transitionProfile(
                tempo = 92.0,
                firstBeatMs = 690.0,
                confidence = 0.52f,
                musicStartMs = 0,
                introEndMs = 10_400,
                outroStartMs = 116_600,
                musicEndMs = 125_200,
            )

        val plan = overlap(planFor(outgoing = moBoy, incoming = matue1993))

        assertEquals("beat-match", plan.technique)
        assertEquals(8_000L, plan.overlapMs, "three bars of 2.667 s; four would need 10.4 s of a 9.7 s intro")
    }

    @Test
    fun `an intro shorter than three bars is a plain crossfade`() {
        assertEquals(TransitionPlan.Fade(4_000), planFor(incoming = transitionProfile(introEndMs = 4_000)))
    }

    @Test
    fun `a song too short for three bars is a plain crossfade`() {
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
        val tapped = transition(cause = TransitionCause.TAP_JUMP)

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

    private val talkPlanner =
        TransitionPlanner(
            listOf(CrossfadeTechnique(), BeatMatchTechnique(), TalkOutroTechnique()),
            slice4.copy(talkEnabled = true),
        )

    private fun describe(plan: TransitionPlan): String =
        when (plan) {
            is TransitionPlan.Overlap ->
                "${plan.technique}:${plan.switchPointMs}:${plan.incomingStartMs}:${plan.overlapMs}:" +
                    "${plan.incomingSpeed}:${plan.easeBack.size}:${plan.bass != null}"
            else -> plan.toString()
        }

    @Test
    fun `a talking outro is planned before the beat match, even when the tempos would match`() {
        val plan = overlap(planFor(outgoing = talkOutroProfile(), planner = talkPlanner))

        assertEquals("talk-outro", plan.technique)
        assertEquals(161_000L, plan.switchPointMs)
    }

    @Test
    fun `songs that are not talk outros are planned exactly as without the talk technique`() {
        val pairs =
            listOf(
                transitionProfile() to transitionProfile(),
                transitionProfile() to transitionProfile(tempo = 123.6),
                transitionProfile() to transitionProfile(tempo = 60.0),
                transitionProfile() to transitionProfile(tempo = 150.0),
                transitionProfile() to transitionProfile(confidence = 0.01f),
                transitionProfile(energy = energyCurve(tail = 0.15f)) to transitionProfile(),
                talkOutroProfile(tail = 0.14f) to transitionProfile(),
                talkOutroProfile(outroStartMs = 171_000) to transitionProfile(tempo = 123.6),
                null to transitionProfile(),
                transitionProfile() to null,
            )

        for ((outgoing, incoming) in pairs) {
            assertEquals(
                describe(planFor(outgoing, incoming)),
                describe(planFor(outgoing, incoming, planner = talkPlanner)),
            )
        }
    }

    @Test
    fun `a talking outro with an incoming song without a profile is the phase 6 crossfade`() {
        val plan = planFor(outgoing = talkOutroProfile(), incoming = null, planner = talkPlanner)

        assertEquals(TransitionPlan.Fade(4_000), plan)
    }

    @Test
    fun `the phase 6 rules come before a talking outro`() {
        fun track(id: Long) =
            transitionSong(id).copy(
                albumId = AlbumId(7),
                albumTitle = "Album",
                albumArtist = "Artist",
                trackNumber = id.toInt(),
            )
        val first = track(1)
        val second = track(2)
        val albumInOrder = transition(outgoing = first, incoming = second, shuffled = false)
        val crossfade = dj.copy(style = TransitionStyle.CROSSFADE)
        val tapped = transition(cause = TransitionCause.TAP_JUMP)

        assertEquals(
            TransitionPlan.HardCut,
            planFor(outgoing = talkOutroProfile(), transition = tapped, planner = talkPlanner),
        )
        assertEquals(
            TransitionPlan.HardCut,
            planFor(outgoing = talkOutroProfile(), transition = albumInOrder, planner = talkPlanner),
        )
        assertEquals(
            TransitionPlan.Fade(4_000),
            planFor(outgoing = talkOutroProfile(), settings = crossfade, planner = talkPlanner),
        )
    }
}
