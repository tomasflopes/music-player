package dev.tomasflopes.musicplayer.playback.crossfade

import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import dev.tomasflopes.musicplayer.core.model.crossfade.CrossfadeDecision
import dev.tomasflopes.musicplayer.core.model.transition.BassCurve
import dev.tomasflopes.musicplayer.core.model.transition.BeatGrid
import dev.tomasflopes.musicplayer.core.model.transition.DjVolumeCurve
import dev.tomasflopes.musicplayer.core.model.transition.SpeedStep
import dev.tomasflopes.musicplayer.core.model.transition.TransitionConfig
import dev.tomasflopes.musicplayer.core.model.transition.TransitionPlan
import dev.tomasflopes.musicplayer.playback.bass.BassControl
import dev.tomasflopes.musicplayer.playback.bass.BassControlSource
import dev.tomasflopes.musicplayer.playback.bass.NoBassControl
import dev.tomasflopes.musicplayer.playback.bass.isBypassed
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
 * [TwoPlayerCrossfadeEngine] with the DJ overlap of phase 7, on two [VirtualPlayer]s and a fake
 * clock (ADR 0092): the overlap starts at the switch point, at the incoming offset, follows the
 * DJ curve  and ends at speed 1.0; a late preload runs the phase 6 plan; a pause, a skip and a
 * release put  both players back at speed 1.0. Slice 2 (ADR 0094 to 0097): a stretched incoming
 * song is read by its position times the speed, eases back step by step on the plan's schedule,
 * phase lock nudges its speed or gives the match up, and every way out ends at speed 1.0.
 * Nothing plays audio.
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

    /** A bass control that remembers what it was told (slice 3, ADR 0102). */
    private class RecordingBass : BassControl {
        override var targetDb = 0f
            private set
        val sets = mutableListOf<Float>()

        override fun setGainDb(db: Float) {
            targetDb = db
            sets += db
        }

        override fun bypass() {
            targetDb = 0f
        }
    }

    private class Setup(
        val outgoing: VirtualPlayer,
        val spare: VirtualPlayer,
        val host: RecordingHost,
        val engine: TwoPlayerCrossfadeEngine,
        val outgoingBass: RecordingBass,
        val spareBass: RecordingBass,
    )

    private fun overlapPlan(
        speed: Float = 1f,
        overlapMs: Long = OVERLAP_MS,
        incomingStartMs: Long = INCOMING_START_MS,
        easeBack: List<SpeedStep> = emptyList(),
        grids: BeatGrid? = null,
        bass: BassCurve? = null,
    ) = PlannedTransition(
        MediaItem.EMPTY,
        CrossfadeDecision.Fade(overlapMs),
        DURATION_MS,
        overlap =
            TransitionPlan.Overlap(
                technique = "beat-match",
                switchPointMs = SWITCH_MS,
                incomingStartMs = incomingStartMs,
                overlapMs = overlapMs,
                curve = DjVolumeCurve,
                ratio = speed.toDouble(),
                incomingSpeed = speed,
                easeBack = easeBack,
                outgoingGrid = grids,
                incomingGrid = grids,
                bass = bass,
            ),
        fallback = CrossfadeDecision.Fade(PHASE_SIX_MS),
    )

    private fun TestScope.setup(
        plan: PlannedTransition = overlapPlan(),
        outgoingStartMs: Long = 0L,
        config: TransitionConfig = TransitionConfig(),
    ): Setup {
        val clock = { currentTime }
        val outgoing =
            VirtualPlayer(clock, DURATION_MS).apply {
                load(outgoingStartMs)
                play()
            }
        val spare = VirtualPlayer(clock, DURATION_MS)
        val host = RecordingHost(outgoing.player, plan, clock)
        val outgoingBass = RecordingBass()
        val spareBass = RecordingBass()
        val controls =
            BassControlSource {
                when {
                    it === outgoing.player -> outgoingBass
                    it === spare.player -> spareBass
                    else -> NoBassControl
                }
            }
        val engine = TwoPlayerCrossfadeEngine(backgroundScope, { spare.player }, attributes, config, controls)
        engine.attach(host)
        engine.invalidate()
        return Setup(outgoing, spare, host, engine, outgoingBass, spareBass)
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

    /** Three bars of ease-back after an overlap whose incoming song covers 7 680 ms of its file at speed 0.96. */
    private val easeSteps =
        listOf(SpeedStep(9_680, 0.97f), SpeedStep(11_680, 0.98f), SpeedStep(13_680, 1f))

    @Test
    fun `a stretched incoming song is read by its position times the speed`() =
        runTest {
            val s = setup(overlapPlan(speed = FASTER))
            advance(SWITCH_MS + 100)

            advance(OVERLAP_MS / 4)
            assertEquals(sqrt(0.5f), s.spare.volume, 0.03f)

            advance(OVERLAP_MS / 2)
            assertEquals(0, s.host.finished, "the overlap is still running")

            advance(OVERLAP_MS / 4 + 500)
            assertEquals(1, s.host.finished)
        }

    @Test
    fun `after the overlap the speed eases back on the plan's schedule and ends at 1`() =
        runTest {
            val s = setup(overlapPlan(speed = SLOWER, easeBack = easeSteps))
            advance(SWITCH_MS + 100)
            assertEquals(SLOWER, s.spare.speed)
            assertEquals(1f, s.outgoing.speed)

            advance(OVERLAP_MS + 300)
            assertEquals(0.97f, s.spare.speed)

            advance(2_000)
            assertEquals(0.98f, s.spare.speed)

            advance(2_000)
            assertEquals(1f, s.spare.speed)
            assertEquals(1f, s.outgoing.speed)
        }

    @Test
    fun `a pause during the ease-back puts the speed back at 1 and the ease stops`() =
        runTest {
            val s = setup(overlapPlan(speed = SLOWER, easeBack = easeSteps))
            advance(SWITCH_MS + OVERLAP_MS + 400)
            assertEquals(0.97f, s.spare.speed)

            s.engine.onPlayingChanged(false)
            assertEquals(1f, s.spare.speed)
            advance(6_000)

            assertEquals(1f, s.spare.speed)
        }

    @Test
    fun `a skip during the ease-back puts the speed back at 1`() =
        runTest {
            val s = setup(overlapPlan(speed = SLOWER, easeBack = easeSteps))
            advance(SWITCH_MS + OVERLAP_MS + 400)
            assertEquals(0.97f, s.spare.speed)

            s.engine.invalidate()

            assertEquals(1f, s.spare.speed)
            assertEquals(1f, s.outgoing.speed)
        }

    @Test
    fun `a kill during the ease-back puts the speed back at 1`() =
        runTest {
            val s = setup(overlapPlan(speed = SLOWER, easeBack = easeSteps))
            advance(SWITCH_MS + OVERLAP_MS + 400)

            s.engine.release()
            advance(5_000)

            assertEquals(1f, s.spare.speed)
            assertEquals(1f, s.outgoing.speed)
        }

    @Test
    fun `a pause in a stretched overlap ends the match, so no ease-back follows`() =
        runTest {
            val s = setup(overlapPlan(speed = SLOWER, easeBack = easeSteps))
            advance(SWITCH_MS + 2_000)
            assertEquals(SLOWER, s.spare.speed)

            s.engine.onPlayingChanged(false)
            assertEquals(1f, s.spare.speed)
            assertEquals(1f, s.outgoing.speed)
            advance(OVERLAP_MS + 6_000)

            assertEquals(1, s.host.finished)
            assertEquals(1f, s.spare.speed)
        }

    @Test
    fun `with phase lock off an overlap runs at its planned speed whatever the error`() =
        runTest {
            val s = setup(overlapPlan(overlapMs = LONG_OVERLAP_MS, incomingStartMs = OFFSET_START_MS, grids = GRID))
            advance(SWITCH_MS + LONG_OVERLAP_MS / 2)

            assertEquals(1f, s.spare.speed)
            assertEquals(1f, s.spare.volume, 0.01f)
            assertEquals(1f, s.outgoing.volume, 0.01f)
            assertFalse(s.spare.calls.any { it.startsWith("setPlaybackParameters") })
        }

    @Test
    fun `with phase lock on an incoming song that is ahead is slowed by the largest nudge`() =
        runTest {
            val config = TransitionConfig(phaseLockEnabled = true)
            val s =
                setup(
                    overlapPlan(overlapMs = LONG_OVERLAP_MS, incomingStartMs = OFFSET_START_MS, grids = GRID),
                    config = config,
                )

            advance(SWITCH_MS + 600)

            assertEquals(1f - config.maxNudge.toFloat(), s.spare.speed, 1e-4f)
        }

    @Test
    fun `with phase lock on aligned songs are left alone`() =
        runTest {
            val s =
                setup(
                    overlapPlan(overlapMs = LONG_OVERLAP_MS, grids = GRID),
                    config = TransitionConfig(phaseLockEnabled = true),
                )

            advance(SWITCH_MS + LONG_OVERLAP_MS / 2)

            assertEquals(1f, s.spare.speed)
            assertFalse(s.spare.calls.any { it.startsWith("setPlaybackParameters") })
        }

    @Test
    fun `a phase error that stays above the limit for two bars turns the rest into an equal-power fade`() =
        runTest {
            val config = TransitionConfig(phaseLockEnabled = true)
            val s =
                setup(
                    overlapPlan(overlapMs = LONG_OVERLAP_MS, incomingStartMs = OFFSET_START_MS, grids = GRID),
                    config = config,
                )

            advance(SWITCH_MS + LONG_OVERLAP_MS / 2 + 50)

            // The DJ curve would hold both players at 1 here; the abandoned match is already falling
            // and rising short of it.
            assertTrue(s.spare.volume < 0.95f, "incoming ${s.spare.volume}")
            assertTrue(s.outgoing.volume < 0.95f, "outgoing ${s.outgoing.volume}")

            advance(LONG_OVERLAP_MS)

            assertEquals(1, s.host.finished)
            assertEquals(1f, s.spare.volume)
            assertEquals(1f, s.spare.speed)
        }

    private val swap = BassCurve(overlapMs = OVERLAP_MS, swapStartMs = 4_000, swapMs = 1_000, cutDb = -24f)

    @Test
    fun `the spare is preloaded with the cut and the outgoing stays flat until the swap`() =
        runTest {
            val s = setup(overlapPlan(bass = swap))

            advance(SWITCH_MS - 2_000)
            assertEquals(-24f, s.spareBass.targetDb, "cut before the overlap starts")

            advance(2_000 + 1_000)
            assertEquals(-24f, s.spareBass.targetDb)
            assertTrue(s.outgoingBass.isBypassed)
        }

    @Test
    fun `the bass swaps on the middle bar over the swap length and the outgoing keeps the cut`() =
        runTest {
            val s = setup(overlapPlan(bass = swap))
            advance(SWITCH_MS)

            advance(4_500)
            assertEquals(-12f, s.spareBass.targetDb, 3f)
            assertEquals(-12f, s.outgoingBass.targetDb, 3f)

            advance(1_500)
            assertEquals(0f, s.spareBass.targetDb, 0.01f)
            assertEquals(-24f, s.outgoingBass.targetDb, 0.01f)
        }

    @Test
    fun `after the overlap both players are bypassed`() =
        runTest {
            val s = setup(overlapPlan(bass = swap))

            advance(SWITCH_MS + OVERLAP_MS + 1_000)

            assertEquals(1, s.host.finished)
            assertTrue(s.spareBass.isBypassed)
            assertTrue(s.outgoingBass.isBypassed)
        }

    @Test
    fun `a pause in the swap bypasses both players and the swap does not come back`() =
        runTest {
            val s = setup(overlapPlan(bass = swap))
            advance(SWITCH_MS + 100 + 4_500)

            s.engine.onPlayingChanged(false)
            advance(1_000)

            assertTrue(s.spareBass.isBypassed)
            assertTrue(s.outgoingBass.isBypassed)
        }

    @Test
    fun `a skip in the middle of an overlap bypasses both players`() =
        runTest {
            val s = setup(overlapPlan(bass = swap))
            advance(SWITCH_MS + 100 + 2_000)
            assertEquals(-24f, s.spareBass.targetDb)

            s.engine.invalidate()

            assertTrue(s.spareBass.isBypassed)
            assertTrue(s.outgoingBass.isBypassed)
        }

    @Test
    fun `a kill in the middle of an overlap bypasses both players`() =
        runTest {
            val s = setup(overlapPlan(bass = swap))
            advance(SWITCH_MS + 100 + 4_500)

            s.engine.release()

            assertTrue(s.spareBass.isBypassed)
            assertTrue(s.outgoingBass.isBypassed)
        }

    @Test
    fun `a plan without a bass curve never sets a gain`() =
        runTest {
            val s = setup(overlapPlan())

            advance(SWITCH_MS + OVERLAP_MS + 1_000)

            assertTrue(s.spareBass.sets.isEmpty())
            assertTrue(s.outgoingBass.sets.isEmpty())
        }

    @Test
    fun `the phase 6 fade never touches the bass`() =
        runTest {
            val s = setup(plan = PlannedTransition(MediaItem.EMPTY, CrossfadeDecision.Fade(PHASE_SIX_MS), DURATION_MS))

            advance(DURATION_MS)

            assertTrue(s.spareBass.sets.isEmpty())
            assertTrue(s.outgoingBass.sets.isEmpty())
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
        const val FASTER = 1.08f
        const val SLOWER = 0.96f
        const val LONG_OVERLAP_MS = 16_000L

        /** Off the beat by 120 ms against the outgoing song, which plays from 0 on a 500 ms beat. */
        const val OFFSET_START_MS = 2_120L
        val GRID = BeatGrid(0.0, 120.0, 4, 16)
    }
}
