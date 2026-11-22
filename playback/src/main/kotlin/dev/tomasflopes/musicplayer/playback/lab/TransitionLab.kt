package dev.tomasflopes.musicplayer.playback.lab

import android.content.Context
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import dev.tomasflopes.musicplayer.core.model.Song
import dev.tomasflopes.musicplayer.core.model.crossfade.CrossfadeDecision
import dev.tomasflopes.musicplayer.core.model.transition.BeatGrid
import dev.tomasflopes.musicplayer.core.model.transition.BeatPhase
import dev.tomasflopes.musicplayer.core.model.transition.TransitionConfig
import dev.tomasflopes.musicplayer.core.model.transition.TransitionPlan
import dev.tomasflopes.musicplayer.playback.crossfade.CrossfadeHost
import dev.tomasflopes.musicplayer.playback.crossfade.PlannedTransition
import dev.tomasflopes.musicplayer.playback.crossfade.TwoPlayerCrossfadeEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

/** What the lab plays: two songs, the overlap planned for them and the two beat grids it measures against. */
class LabSetup(
    val outgoing: Song,
    val incoming: Song,
    val plan: TransitionPlan.Overlap,
    val outgoingGrid: BeatGrid,
    val incomingGrid: BeatGrid,
)

enum class LabPhase { IDLE, WAITING, OVERLAP, AFTER, DRIFT }

/** One reading every [TransitionLab.READING_MS]: file positions; the error is the incoming beat minus the outgoing. */
data class LabReading(
    val phase: LabPhase = LabPhase.IDLE,
    val outgoingMs: Long = 0,
    val incomingMs: Long = -1,
    val phaseErrorMs: Double = Double.NaN,
    val incomingSpeed: Float = 1f,
)

/** What the owner reports from the phone (design phase 7, slice 1); NaN until measured. */
data class LabSummary(
    /** The beat-phase error in the first readings of the overlap, or of the drift test. */
    val startErrorMs: Double = Double.NaN,
    /** The error in the last reading of the overlap, or of the drift test (30 s). */
    val endErrorMs: Double = Double.NaN,
    val maxAbsErrorMs: Double = Double.NaN,
) {
    val driftMs: Double get() = endErrorMs - startErrorMs
}

data class LabState(
    val reading: LabReading = LabReading(),
    val summary: LabSummary = LabSummary(),
    /** Every change of the incoming speed, with the time since the lab started, oldest first. */
    val speedLog: List<String> = emptyList(),
    val message: String = "",
)

/**
 * The Transition lab's player side (design phase 7, ADR 0093): two ExoPlayers of its own, apart from the session, and
 * the same [TwoPlayerCrossfadeEngine] the app uses, driven by a small host of its own. It plays the plan from
 * [TransitionConfig.labLeadMs] before the switch point to [AFTER_MS] after the overlap, reads the beat-phase error and
 * the incoming speed every [READING_MS], and can change the speed mid-play (1.0, 1.04, 1.0) to hear whether that is
 * audible. A drift test plays both songs aligned for [DRIFT_MS] without any overlap. Everything runs on the thread of
 * [scope], which must be the main thread.
 */
class TransitionLab(
    private val context: Context,
    private val scope: CoroutineScope,
    private val config: TransitionConfig = TransitionConfig(),
) {
    private val mutableState = MutableStateFlow(LabState())
    val state: StateFlow<LabState> = mutableState.asStateFlow()
    private var session: Session? = null

    fun startTransition(setup: LabSetup) = begin { Session(setup).also { it.startTransition() } }

    fun startDrift(setup: LabSetup) = begin { Session(setup).also { it.startDrift() } }

    /** 1.0, then 1.04 for [SPEED_TEST_MS], then 1.0 again, on the player that is playing the incoming song. */
    fun changeSpeed() {
        session?.speedTest()
    }

    fun stop() {
        session?.release()
        session = null
        mutableState.update { it.copy(reading = LabReading(), message = "Stopped") }
    }

    private fun begin(create: () -> Session) {
        stop()
        mutableState.value = LabState(message = "Starting")
        session = create()
    }

    private inner class Session(
        private val setup: LabSetup,
    ) {
        private val attributes =
            AudioAttributes
                .Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build()
        private val first = ExoPlayer.Builder(context).build()
        private val second = ExoPlayer.Builder(context).build()
        private val engine = TwoPlayerCrossfadeEngine(scope, { second }, attributes, config)
        private val host = LabHost(first, plannedTransition())
        private val startedAt = System.nanoTime()
        private var job: Job? = null
        private var lastSpeed = 1f
        private var startError = Double.NaN
        private var maxAbs = 0.0

        init {
            first.setAudioAttributes(attributes, true)
            second.setAudioAttributes(attributes, false)
        }

        fun startTransition() {
            val at = (setup.plan.switchPointMs - config.labLeadMs).coerceAtLeast(0)
            first.setMediaItem(item(setup.outgoing), at)
            first.prepare()
            first.playWhenReady = true
            engine.attach(host)
            engine.invalidate()
            job = scope.launch { sample() }
        }

        fun startDrift() {
            first.setMediaItem(item(setup.outgoing), setup.plan.switchPointMs)
            second.setMediaItem(item(setup.incoming), setup.plan.incomingStartMs)
            first.volume = DRIFT_VOLUME
            second.volume = DRIFT_VOLUME
            first.prepare()
            second.prepare()
            job = scope.launch { drift() }
        }

        fun speedTest() {
            val player = host.incomingOrNull() ?: return
            scope.launch {
                player.playbackParameters = PlaybackParameters(SPEED_TEST_SPEED)
                delay(SPEED_TEST_MS)
                if (scope.isActive && player.playbackParameters.speed == SPEED_TEST_SPEED) {
                    player.playbackParameters = PlaybackParameters.DEFAULT
                }
            }
        }

        fun release() {
            job?.cancel()
            engine.release()
            first.release()
            second.release()
        }

        private fun plannedTransition() =
            PlannedTransition(
                incoming = item(setup.incoming),
                decision = CrossfadeDecision.Fade(setup.plan.overlapMs),
                outgoingDurationMs = setup.outgoing.durationMs,
                overlap = setup.plan,
                fallback = CrossfadeDecision.Fade(FALLBACK_FADE_MS),
            )

        private fun item(song: Song) = MediaItem.fromUri(Uri.parse(song.contentUri))

        private suspend fun sample() {
            while (scope.isActive) {
                delay(READING_MS)
                val over = host.finished && host.sinceFinishedMs() >= AFTER_MS
                publish(reading())
                if (over) {
                    stop()
                    return
                }
            }
        }

        private fun reading(): LabReading {
            val incoming = host.incomingOrNull()
            val retiring = host.retiring
            if (incoming == null || retiring == null) {
                return LabReading(LabPhase.WAITING, outgoingMs = first.currentPosition)
            }
            val inPos = incoming.currentPosition
            val speed = incoming.playbackParameters.speed
            if (host.finished) {
                return LabReading(LabPhase.AFTER, outgoingMs = -1, incomingMs = inPos, incomingSpeed = speed)
            }
            val outPos = retiring.currentPosition
            return LabReading(LabPhase.OVERLAP, outPos, inPos, errorMs(outPos, inPos), speed)
        }

        private suspend fun drift() {
            while (scope.isActive && !(first.isReady() && second.isReady())) delay(READING_MS)
            first.playWhenReady = true
            second.playWhenReady = true
            val began = System.nanoTime()
            while (scope.isActive) {
                delay(READING_MS)
                val outPos = first.currentPosition
                val inPos = second.currentPosition
                val speed = second.playbackParameters.speed
                publish(LabReading(LabPhase.DRIFT, outPos, inPos, errorMs(outPos, inPos), speed))
                if (System.nanoTime() - began >= DRIFT_MS * NANOS_PER_MS) {
                    stop()
                    return
                }
            }
        }

        private fun publish(reading: LabReading) {
            val error = reading.phaseErrorMs
            if (!error.isNaN()) {
                if (startError.isNaN()) startError = error
                maxAbs = maxOf(maxAbs, abs(error))
            }
            val changed = reading.phase != LabPhase.WAITING && reading.incomingSpeed != lastSpeed
            val line =
                if (changed) listOf("t=${elapsedMs()} ms  speed $lastSpeed to ${reading.incomingSpeed}") else emptyList()
            if (changed) lastSpeed = reading.incomingSpeed
            mutableState.update {
                it.copy(
                    reading = reading,
                    summary = LabSummary(startError, if (error.isNaN()) it.summary.endErrorMs else error, maxAbs),
                    speedLog = it.speedLog + line,
                    message = "",
                )
            }
        }

        private fun elapsedMs() = (System.nanoTime() - startedAt) / NANOS_PER_MS

        private fun ExoPlayer.isReady() = playbackState == Player.STATE_READY

        private fun errorMs(
            outgoingPos: Long,
            incomingPos: Long,
        ) = BeatPhase.errorMs(setup.outgoingGrid, outgoingPos.toDouble(), setup.incomingGrid, incomingPos.toDouble())
    }

    /** The playback engine's part for the lab: plans once and notes the two players at the hand-over. */
    private class LabHost(
        first: ExoPlayer,
        private val plan: PlannedTransition,
    ) : CrossfadeHost {
        private var active: ExoPlayer = first
        override val activePlayer: ExoPlayer get() = active
        var retiring: ExoPlayer? = null
            private set
        var finished = false
            private set
        private var finishedAt = 0L
        private var planned = false

        override suspend fun planNext(): PlannedTransition? = if (planned) null else plan.also { planned = true }

        override suspend fun fadeStarted(incoming: ExoPlayer) {
            retiring = active
            active = incoming
        }

        override suspend fun fadeFinished() {
            finished = true
            finishedAt = System.nanoTime()
        }

        /** The player of the incoming song once the overlap has started, otherwise null. */
        fun incomingOrNull(): ExoPlayer? = if (retiring == null) null else active

        fun sinceFinishedMs(): Long = (System.nanoTime() - finishedAt) / NANOS_PER_MS
    }

    companion object {
        const val READING_MS = 100L

        /** The lab plays this long after the overlap, then stops. */
        const val AFTER_MS = 10_000L

        const val DRIFT_MS = 30_000L
        const val SPEED_TEST_MS = 4_000L
        const val SPEED_TEST_SPEED = 1.04f
        private const val DRIFT_VOLUME = 0.5f
        private const val FALLBACK_FADE_MS = 4_000L
        private const val NANOS_PER_MS = 1_000_000L
    }
}
