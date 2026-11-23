package dev.tomasflopes.musicplayer.core.model.transition

import dev.tomasflopes.musicplayer.core.model.crossfade.CrossfadeSettings
import dev.tomasflopes.musicplayer.core.model.crossfade.Transition
import dev.tomasflopes.musicplayer.core.model.crossfade.TransitionCause
import dev.tomasflopes.musicplayer.core.model.crossfade.TransitionStyle
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** The debug tally of the techniques picked and the reasons for a fall-through (phase 7b, ADR 0112). */
class TransitionTallyTest {
    private val dj = CrossfadeSettings(enabled = true, durationMs = 4_000, style = TransitionStyle.DJ_MIX)
    private val tally = TransitionTally()
    private val techniques =
        listOf(
            CrossfadeTechnique(),
            BeatMatchTechnique(),
            TalkOutroTechnique(CalibratedConfidenceReliability()),
        )
    private val planner = TransitionPlanner(techniques, TransitionConfig(), tally)

    private fun plan(
        outgoing: TransitionProfile? = transitionProfile(),
        incoming: TransitionProfile? = transitionProfile(),
        outgoingDurationMs: Long = 180_000,
        cause: TransitionCause = TransitionCause.END_OF_SONG,
        planner: TransitionPlanner = this.planner,
    ) = planner.plan(
        dj,
        Transition(transitionSong(1, outgoingDurationMs), transitionSong(2), cause, shuffled = true, repeatOne = false),
        outgoing,
        incoming,
    )

    private fun fallThroughs() = tally.snapshot().fallThroughs

    @Test
    fun `a beat match and a talking outro are counted under their own names`() {
        plan()
        plan()
        plan(outgoing = talkOutroProfile())

        assertEquals(mapOf("beat-match" to 2, "talk-outro" to 1), tally.snapshot().techniques)
        assertEquals(emptyMap<FallThrough, Int>(), fallThroughs())
        assertEquals(3, tally.snapshot().total)
    }

    @Test
    fun `a missing profile falls through for want of a profile`() {
        plan(incoming = null)

        assertEquals(mapOf(FallThrough.NO_PROFILE to 1), fallThroughs())
        assertEquals(1, tally.snapshot().crossfades)
    }

    @Test
    fun `a low tempo confidence falls through for confidence`() {
        plan(incoming = transitionProfile(confidence = 0.05f))

        assertEquals(mapOf(FallThrough.CONFIDENCE to 1), fallThroughs())
    }

    @Test
    fun `tempos too far apart fall through for tempo`() {
        plan(incoming = transitionProfile(tempo = 150.0))

        assertEquals(mapOf(FallThrough.TEMPO to 1), fallThroughs())
    }

    @Test
    fun `an intro too short for the smallest overlap falls through for the intro`() {
        plan(incoming = transitionProfile(introEndMs = 3_000))

        assertEquals(mapOf(FallThrough.INTRO to 1), fallThroughs())
    }

    @Test
    fun `a song whose music ends too soon for the overlap falls through for the tail`() {
        plan(outgoing = transitionProfile(introEndMs = 1_000, outroStartMs = 3_000, musicEndMs = 5_000))

        assertEquals(mapOf(FallThrough.TAIL to 1), fallThroughs())
    }

    @Test
    fun `a hard cut of the phase 6 rules never reaches the planner and is not counted by it`() {
        plan(cause = TransitionCause.TAP_JUMP)

        assertEquals(0, tally.snapshot().total)
    }

    @Test
    fun `the engine counts a hard cut by name and it is not a crossfade`() {
        tally.record(TransitionPlan.HardCut.technique)

        assertEquals(mapOf("hard-cut" to 1), tally.snapshot().techniques)
        assertEquals(emptyMap<FallThrough, Int>(), fallThroughs())
    }

    @Test
    fun `a planner for the lab counts nothing, and reset clears the counts`() {
        plan(planner = planner.withConfig(TransitionConfig(overlapBars = 4)))

        assertEquals(0, tally.snapshot().total)

        plan(incoming = null)
        tally.reset()

        assertEquals(0, tally.snapshot().total)
        assertEquals(emptyMap<FallThrough, Int>(), fallThroughs())
    }

    @Test
    fun `a crossfade with no reason given counts as other`() {
        tally.record("crossfade", null)

        assertEquals(mapOf(FallThrough.OTHER to 1), fallThroughs())
    }

    @Test
    fun `whyNot is null when the beat match applies and a talk or crossfade technique says nothing`() {
        val request = transitionRequest()

        assertNull(BeatMatchTechnique().whyNot(request))
        assertNull(CrossfadeTechnique().whyNot(request))
        assertNull(TalkOutroTechnique(CalibratedConfidenceReliability()).whyNot(request))
    }
}
