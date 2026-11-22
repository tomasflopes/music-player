package dev.tomasflopes.musicplayer.playback.crossfade

import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import dev.tomasflopes.musicplayer.core.model.crossfade.CrossfadeDecision
import dev.tomasflopes.musicplayer.core.model.transition.DjVolumeCurve
import dev.tomasflopes.musicplayer.core.model.transition.TransitionPlan
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.sqrt

/**
 * [TwoPlayerCrossfadeEngine] with the DJ overlap of phase 7, on two [VirtualPlayer]s and a fake clock (ADR 0092): the
 * overlap starts at the switch point, at the incoming offset, follows the DJ curve and ends at speed 1.0; a late
 * preload runs the phase 6 plan; a pause, a skip and a release put both players back at speed 1.0. Nothing plays audio.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TwoPlayerTransitionEngineTest {
    private val attributes =
        AudioAttributes
            .Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

    /** Plays the playback engine's part: plans once, and notes what the engine did at the handover. */
    private class RecordingHost(
        var active: ExoPlayer,
        private val plan: PlannedTransition,
        private val clock: () -> Long,
    ) : CrossfadeHost {
        var retiring: ExoPlayer? = null
        var startedAt = -1L
        var outgoingPositionAtStart = -1L
        var incomingPositionAtStart = -1L
        var incomingVolumeAtStart = -1f
        var started = 0
        var finished = 0
        private var planned = false

        override val activePlayer: ExoPlayer get() = active

        override suspend fun planNext(): PlannedTransition? = if (planned) null else plan.also { planned = true }

        override suspend fun fadeStarted(incoming: ExoPlayer) {
            startedAt = clock()
            outgoingPositionAtStart = active.currentPosition
            incomingPositionAtStart = incoming.currentPosition
            incomingVolumeAtStart = incoming.volume
            retiring = active
            active = incoming
            started++
        }

        override suspend fun fadeFinished() {
            finished++
        }
    }

    private class Setup(
        val outgoing: VirtualPlayer,
        val spare: VirtualPlayer,
        val host: RecordingHost,
        val engine: TwoPlayerCrossfadeEngine,
    )

    private fun overlapPlan(speed: Float = 1f) =
        PlannedTransition(
            MediaItem.EMPTY,
            CrossfadeDecision.Fade(OVERLAP_MS),
            DURATION_MS,
            overlap =
                TransitionPlan.Overlap(
                    technique = "beat-match",
                    switchPointMs = SWITCH_MS,
                    incomingStartMs = INCOMING_START_MS,
                    overlapMs = OVERLAP_MS,
                    curve = DjVolumeCurve,
                    incomingSpeed = speed,
                ),
            fallback = CrossfadeDecision.Fade(PHASE_SIX_MS),
        )

    private fun TestScope.setup(
        plan: PlannedTransition = overlapPlan(),
        outgoingStartMs: Long = 0L,
    ): Setup {
        val clock = { currentTime }
        val outgoing =
            VirtualPlayer(clock, DURATION_MS).apply {
                load(outgoingStartMs)
                play()
            }
        val spare = VirtualPlayer(clock, DURATION_MS)
        val host = RecordingHost(outgoing.player, plan, clock)
        val engine = TwoPlayerCrossfadeEngine(backgroundScope, { spare.player }, attributes)
        engine.attach(host)
        engine.invalidate()
        return Setup(outgoing, spare, host, engine)
    }

    private fun TestScope.advance(ms: Long) {
        advanceTimeBy(ms)
        runCurrent()
    }

    @Test
    fun `an overlap starts at the switch point at the incoming offset and ends with the incoming alone`() =
        runTest {
            val s = setup()

            advance(SWITCH_MS - 500)
            assertEquals(0, s.host.started, "started before the switch point")
            advance(700)

            assertEquals(1, s.host.started)
            assertNear(SWITCH_MS, s.host.outgoingPositionAtStart)
            assertTrue(s.host.incomingPositionAtStart in INCOMING_START_MS..(INCOMING_START_MS + 100))
            assertEquals(0f, s.host.incomingVolumeAtStart)
            assertTrue(s.spare.playWhenReady)

            advance(OVERLAP_MS + 500)

            assertEquals(1, s.host.finished)
            assertEquals(1f, s.spare.volume)
            assertEquals(0f, s.outgoing.volume)
            assertFalse(s.outgoing.hasItem, "the retired player is emptied")
            assertEquals(1f, s.spare.speed)
            assertEquals(1f, s.outgoing.speed)
        }

    @Test
    fun `the volumes follow the DJ curve of the incoming position`() =
        runTest {
            val s = setup()
            advance(SWITCH_MS + 100)

            advance(OVERLAP_MS / 4)
            assertEquals(sqrt(0.5f), s.spare.volume, 0.05f)
            assertEquals(1f, s.outgoing.volume, 0.01f)

            advance(OVERLAP_MS / 4)
            assertEquals(1f, s.spare.volume, 0.01f)
            assertEquals(1f, s.outgoing.volume, 0.05f)

            advance(OVERLAP_MS / 4)
            assertEquals(1f, s.spare.volume, 0.01f)
            assertEquals(sqrt(0.5f), s.outgoing.volume, 0.05f)
        }

    @Test
    fun `a spare that is not ready at the switch point runs the phase 6 plan`() =
        runTest {
            val s = setup()
            s.spare.ready = false
            advance(SWITCH_MS + 2_000)
            assertEquals(0, s.host.started, "no overlap without a ready spare")
            s.spare.ready = true

            advance(DURATION_MS - SWITCH_MS)

            assertEquals(1, s.host.started)
            assertNear(DURATION_MS - PHASE_SIX_MS, s.host.outgoingPositionAtStart)
            assertTrue(s.host.incomingPositionAtStart < 200, "the phase 6 fade starts the song from 0")
        }

    @Test
    fun `a switch point that is already past runs the phase 6 plan`() =
        runTest {
            val s = setup(outgoingStartMs = SWITCH_MS + 15_000)

            advance(DURATION_MS)

            assertEquals(1, s.host.started)
            assertTrue(s.host.incomingPositionAtStart < 200)
        }

    @Test
    fun `a plan without an overlap is the phase 6 fade, unchanged`() =
        runTest {
            val s = setup(plan = PlannedTransition(MediaItem.EMPTY, CrossfadeDecision.Fade(PHASE_SIX_MS), DURATION_MS))

            advance(DURATION_MS)

            assertEquals(1, s.host.started)
            assertEquals(1, s.host.finished)
            assertNear(DURATION_MS - PHASE_SIX_MS, s.host.outgoingPositionAtStart)
        }

    @Test
    fun `both players are back at speed 1 after the overlap`() =
        runTest {
            val s = setup(overlapPlan(speed = SPED_UP))
            advance(SWITCH_MS + 1_000)
            assertEquals(SPED_UP, s.spare.speed)

            advance(OVERLAP_MS + 1_000)

            assertEquals(1f, s.spare.speed)
            assertEquals(1f, s.outgoing.speed)
        }

    @Test
    fun `a pause in the middle of an overlap puts both players back at speed 1`() =
        runTest {
            val s = setup(overlapPlan(speed = SPED_UP))
            advance(SWITCH_MS + 1_000)
            s.outgoing.player.speedUp()

            s.engine.onPlayingChanged(false)

            assertEquals(1f, s.spare.speed)
            assertEquals(1f, s.outgoing.speed)
        }

    @Test
    fun `a skip in the middle of an overlap puts both players back at speed 1`() =
        runTest {
            val s = setup(overlapPlan(speed = SPED_UP))
            advance(SWITCH_MS + 1_000)
            s.outgoing.player.speedUp()

            s.engine.invalidate()

            assertEquals(1f, s.spare.speed)
            assertEquals(1f, s.outgoing.speed)
            assertEquals(1f, s.spare.volume, "the incoming song carries on at full volume")
        }

    @Test
    fun `a kill in the middle of an overlap puts both players back at speed 1`() =
        runTest {
            val s = setup(overlapPlan(speed = SPED_UP))
            advance(SWITCH_MS + 1_000)
            s.outgoing.player.speedUp()

            s.engine.release()

            assertEquals(1f, s.spare.speed)
            assertEquals(1f, s.outgoing.speed)
        }

    private fun assertNear(
        expected: Long,
        actual: Long,
    ) = assertTrue(actual in expected..(expected + 100), "expected about $expected but was $actual")

    private fun ExoPlayer.speedUp() {
        playbackParameters = androidx.media3.common.PlaybackParameters(SPED_UP)
    }

    private companion object {
        const val DURATION_MS = 60_000L
        const val SWITCH_MS = 30_000L
        const val INCOMING_START_MS = 2_000L
        const val OVERLAP_MS = 8_000L
        const val PHASE_SIX_MS = 4_000L
        const val SPED_UP = 1.04f
    }
}
