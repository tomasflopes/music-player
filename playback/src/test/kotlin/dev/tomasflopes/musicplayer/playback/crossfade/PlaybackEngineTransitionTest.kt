package dev.tomasflopes.musicplayer.playback.crossfade

import dev.tomasflopes.musicplayer.core.model.crossfade.CrossfadeDecision
import dev.tomasflopes.musicplayer.core.model.crossfade.CrossfadeSettings
import dev.tomasflopes.musicplayer.core.model.crossfade.TransitionStyle
import dev.tomasflopes.musicplayer.core.model.log.EndedBy
import dev.tomasflopes.musicplayer.core.model.log.PlaySource
import dev.tomasflopes.musicplayer.core.model.log.StartedBy
import dev.tomasflopes.musicplayer.core.model.transition.BeatMatchTechnique
import dev.tomasflopes.musicplayer.core.model.transition.CrossfadeTechnique
import dev.tomasflopes.musicplayer.core.model.transition.TransitionPlanner
import dev.tomasflopes.musicplayer.core.model.transition.TransitionProfile
import dev.tomasflopes.musicplayer.core.model.transition.TransitionProfileSource
import dev.tomasflopes.musicplayer.core.model.transition.transitionProfile
import dev.tomasflopes.musicplayer.playback.EngineHarness
import dev.tomasflopes.musicplayer.playback.PlayRequest
import dev.tomasflopes.musicplayer.playback.queue.RepeatMode
import dev.tomasflopes.musicplayer.playback.queue.ref
import dev.tomasflopes.musicplayer.playback.queue.refs
import dev.tomasflopes.musicplayer.playback.song
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * The playback engine as the host of a DJ transition (ADR 0092), with a fake crossfade engine and a fake clock like
 * [PlaybackEngineCrossfadeTest]: what it plans for each style, which profiles it reads, and that an overlap still
 * writes one log row per track with the overlap booked to the outgoing row (ADR 0062).
 */
class PlaybackEngineTransitionTest {
    private val fake = FakeCrossfadeEngine()
    private val dj = CrossfadeSettings(enabled = true, durationMs = 4_000, style = TransitionStyle.DJ_MIX)
    private val planner = TransitionPlanner(listOf(BeatMatchTechnique(), CrossfadeTechnique()))

    /** Profile reads, so a test can tell which songs the engine asked about. */
    private class Profiles(
        private val byId: Map<Long, TransitionProfile?>,
    ) : TransitionProfileSource {
        val asked = mutableListOf<Long>()

        override suspend fun profile(songId: Long): TransitionProfile? {
            asked += songId
            return byId[songId]
        }
    }

    private val both =
        Profiles(mapOf(ref('A').songId.value to transitionProfile(), ref('B').songId.value to transitionProfile()))

    private fun playing(
        settings: CrossfadeSettings = dj,
        profiles: TransitionProfileSource = both,
    ): EngineHarness {
        val harness =
            EngineHarness(
                "ABCD",
                crossfade = fake,
                crossfadeSettings = { settings },
                songOf = { song(it, album = "Album $it") },
                planner = planner,
                profiles = profiles,
            )
        harness.engine.play(PlayRequest(refs("ABCD"), 0, shuffle = false, PlaySource.ALBUM, picked = true))
        harness.state.playing = true
        harness.state.listener?.onIsPlayingChanged(true)
        return harness
    }

    @Test
    fun `plans a beat match for two profiled songs in the DJ style`() {
        playing()

        val plan = runBlocking { fake.host.planNext() }!!
        val overlap = plan.overlap

        assertNotNull(overlap)
        assertEquals("beat-match", overlap!!.technique)
        assertEquals(CrossfadeDecision.Fade(overlap.overlapMs), plan.decision)
        assertEquals(CrossfadeDecision.Fade(4_000), plan.fallback)
        assertEquals(CrossfadeDecision.Fade(4_000), plan.phaseSix().decision)
        assertNull(plan.phaseSix().overlap)
    }

    @Test
    fun `the Crossfade style is phase 6 and reads no profile`() {
        val profiles = Profiles(emptyMap())
        playing(dj.copy(style = TransitionStyle.CROSSFADE), profiles)

        val plan = runBlocking { fake.host.planNext() }!!

        assertEquals(CrossfadeDecision.Fade(4_000), plan.decision)
        assertNull(plan.overlap)
        assertEquals(emptyList<Long>(), profiles.asked)
    }

    @Test
    fun `a missing profile gives the phase 6 crossfade`() {
        playing(profiles = Profiles(mapOf(ref('A').songId.value to transitionProfile())))

        val plan = runBlocking { fake.host.planNext() }!!

        assertEquals(CrossfadeDecision.Fade(4_000), plan.decision)
        assertNull(plan.overlap)
    }

    @Test
    fun `a hard cut rule runs before any technique and reads no profile`() {
        val profiles = Profiles(emptyMap())
        val h = playing(profiles = profiles)
        h.engine.setRepeat(RepeatMode.ONE)

        val plan = runBlocking { fake.host.planNext() }!!

        assertEquals(CrossfadeDecision.HardCut, plan.decision)
        assertEquals(emptyList<Long>(), profiles.asked)
    }

    @Test
    fun `a seek by the owner makes the end of the song a hard cut in the DJ style too`() {
        val h = playing()

        h.engine.exposed.seekTo(30_000L)

        assertEquals(CrossfadeDecision.HardCut, runBlocking { fake.host.planNext() }?.decision)
    }

    @Test
    fun `an overlap writes one row per track and the incoming row does not count the overlap`() {
        val h = playing()
        h.time += 150_000
        h.secondState.itemCount = 1
        h.secondState.playing = true
        h.secondState.position = 500
        runBlocking { fake.host.fadeStarted(h.secondPlayer) }
        h.time += 16_000
        runBlocking { fake.host.fadeFinished() }
        h.time += 10_000
        h.engine.release()

        assertEquals(2, h.history.events.size)
        val (outgoing, incoming) = h.history.events
        assertEquals(ref('A').songId, outgoing.songId)
        assertEquals(EndedBy.COMPLETED, outgoing.endedBy)
        assertEquals(180_000L, outgoing.listenedMs)
        assertEquals(ref('B').songId, incoming.songId)
        assertEquals(StartedBy.AUTO, incoming.startedBy)
        assertEquals(10_000L, incoming.listenedMs)
    }

    @Test
    fun `the session shows the incoming song from the overlap start at its own offset`() {
        val h = playing()
        h.secondState.position = 500

        h.secondState.itemCount = 1
        runBlocking { fake.host.fadeStarted(h.secondPlayer) }

        assertEquals(500L, h.engine.exposed.currentPosition)
    }
}
