package dev.tomasflopes.musicplayer.playback.crossfade

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import dev.tomasflopes.musicplayer.core.model.crossfade.CrossfadeDecision
import dev.tomasflopes.musicplayer.core.model.transition.BassCurve
import dev.tomasflopes.musicplayer.core.model.transition.EqualPowerCurve
import dev.tomasflopes.musicplayer.core.model.transition.EqualPowerTail
import dev.tomasflopes.musicplayer.core.model.transition.GainCurve
import dev.tomasflopes.musicplayer.core.model.transition.PhaseLock
import dev.tomasflopes.musicplayer.core.model.transition.SpeedStep
import dev.tomasflopes.musicplayer.core.model.transition.TransitionConfig
import dev.tomasflopes.musicplayer.core.model.transition.TransitionPlan
import dev.tomasflopes.musicplayer.playback.bass.BassControlSource
import dev.tomasflopes.musicplayer.playback.bass.BassPlayers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Two ExoPlayers blended by hand (ADR 0061). The host's player plays the outgoing song. A watcher plans the next
 * transition; when the policy says fade, it preloads the next song on the spare player, silent and paused, and when the
 * outgoing song is `fade` from its end it starts the spare, hands the session and the queue window to it
 * ([CrossfadeHost.fadeStarted]) and ramps both volumes with the plan's curve ([EqualPowerCurve] in phase 6) until the
 * incoming song's position says the fade is over. The outgoing player then becomes the spare. Progress is read from
 * the incoming player's position, not from a timer, so a pause or a seek mid-fade can never leave the two out of step.
 *
 * Whenever the spare is not ready at the moment the fade should start, or the policy says hard cut, nothing happens
 * and Media3's own gapless transition on the host's player does the join. Every state change goes through the main
 * thread; [secondary] is built lazily on the first fade.
 *
 * A DJ overlap (phase 7, ADR 0092) is the same handover with a different start and a different curve: the spare is
 * preloaded at the plan's incoming offset, the overlap starts when the outgoing song reaches the plan's switch point,
 * and the volumes follow the plan's [GainCurve] of the incoming position since that offset. An overlap whose spare is
 * not ready, or whose switch point has passed, runs the phase 6 plan instead.
 *
 * Tempo match (slice 2, ADR 0094 to 0097): the incoming player runs at the plan's speed during the overlap, so its
 * position covers that speed times the overlap; the volumes are read from that. With phase lock on ([PhaseLock]) the
 * speed is nudged on a slow tick, and a lost beat match turns the rest of the overlap into an equal-power fade. When
 * the overlap ends the incoming speed eases back to 1.0 on the plan's schedule, driven by a job of its own that runs
 * next to the watcher. A skip, a seek, a queue or settings change, a pause, the next overlap and a kill end the ease
 * and put both players at speed 1.0 at once.
 */
@Suppress("TooManyFunctions") // one state machine over two players; the ease and phase lock helpers share its fields
class TwoPlayerCrossfadeEngine(
    private val scope: CoroutineScope,
    private val secondary: () -> ExoPlayer,
    private val attributes: AudioAttributes,
    private val config: TransitionConfig = TransitionConfig(),
    private val bass: BassControlSource = BassControlSource.None,
) : CrossfadeEngine {
    private class Fade(
        val retiring: ExoPlayer,
        val incoming: ExoPlayer,
        val durationMs: Long,
        /** Where in its own file the incoming song starts; its position counts from here. */
        val startOffsetMs: Long = 0,
        var curve: GainCurve = EqualPowerCurve,
        /** Set once the host took [incoming] as its player; before that a cancelled fade leaves the host untouched. */
        var handedOver: Boolean = false,
        /** The incoming speed during the overlap: its file covers [speed] times [durationMs]. */
        val speed: Float = 1f,
        var lock: PhaseLock? = null,
        val easeBack: List<SpeedStep> = emptyList(),
        /** False once the speed was put back to 1.0 early (a pause): no ease-back follows the overlap. */
        var stretched: Boolean = false,
        /** The bass swap of a beat match (slice 3); null once it was reset (a pause) or when the plan has none. */
        var bassCurve: BassCurve? = null,
    )

    private var host: CrossfadeHost? = null
    private var spare: ExoPlayer? = null
    private var fade: Fade? = null
    private var job: Job? = null
    private var easeJob: Job? = null
    private var easing: ExoPlayer? = null

    override fun attach(host: CrossfadeHost) {
        this.host = host
    }

    override fun invalidate() {
        cancelEase()
        abortFade()
        job?.cancel()
        spare?.let(::discard)
        job = scope.launch { watch() }
    }

    override fun onPlayingChanged(playing: Boolean) {
        if (!playing) cancelEase()
        val running = fade?.takeIf { it.handedOver } ?: return
        running.retiring.playWhenReady = playing
        if (!playing) {
            resetSpeed(running.retiring)
            resetSpeed(running.incoming)
            // The beats cannot stay matched at speed 1.0: the rest of the overlap is a plain equal-power fade.
            abandonMatch(running)
            running.stretched = false
            resetBass(running)
        }
    }

    override fun release() {
        cancelEase()
        abortFade()
        job?.cancel()
        spare?.let(::resetSpeed)
        spare?.release()
        spare = null
    }

    /** One plan after another: wait for each fade to finish, then plan the one after it. Ends on a hard cut. */
    private suspend fun watch() {
        val host = host ?: return
        var again = true
        while (again) again = fadeNext(host)
    }

    /** Plans the next transition and, when it is a fade, waits for it and runs it. False: stop watching. */
    private suspend fun fadeNext(host: CrossfadeHost): Boolean {
        val plan = host.planNext() ?: return false
        val overlap = plan.overlap
        val ran = overlap != null && waitAndOverlap(host, plan, overlap, sparePlayer())
        return ran || fadePhaseSix(host, if (overlap != null) plan.phaseSix() else plan)
    }

    /** The phase 6 fade of [plan], or false when it is a hard cut. */
    private suspend fun fadePhaseSix(
        host: CrossfadeHost,
        plan: PlannedTransition,
    ): Boolean {
        val requested = (plan.decision as? CrossfadeDecision.Fade)?.durationMs
        return requested != null && waitAndFade(host, plan, requested, sparePlayer())
    }

    private fun sparePlayer(): ExoPlayer = spare ?: secondary().also(::configureSpare).also { spare = it }

    /** True when a fade ran to its end. False when this transition is a plain one (not ready, too late). */
    private suspend fun waitAndFade(
        host: CrossfadeHost,
        plan: PlannedTransition,
        requestedMs: Long,
        player: ExoPlayer,
    ): Boolean {
        var preloaded = false
        while (true) {
            delay(TICK_MS)
            val active = host.activePlayer
            val remaining = remainingMs(active, plan)
            if (!preloaded && remaining <= requestedMs + PRELOAD_LEAD_MS) {
                preload(player, plan)
                preloaded = true
            }
            if (!preloaded || remaining > requestedMs || !active.isPlaying) continue
            val fadeMs = minOf(requestedMs, remaining)
            if (fadeMs < MIN_FADE_MS || player.playbackState != Player.STATE_READY) return false
            runFade(host, Fade(active, player, fadeMs))
            return true
        }
    }

    /**
     * Waits for the outgoing song to reach the switch point of [overlap], preloading the spare at the incoming offset
     * [TransitionConfig.preloadLeadMs] plus the overlap before it, then runs the overlap. True when it ran. False when
     * the spare is not ready at the switch point or the switch point is already past: the phase 6 plan runs instead.
     */
    private suspend fun waitAndOverlap(
        host: CrossfadeHost,
        plan: PlannedTransition,
        overlap: TransitionPlan.Overlap,
        player: ExoPlayer,
    ): Boolean {
        var preloaded = false
        var started: Boolean? = null
        var wait = TICK_MS
        while (started == null) {
            delay(wait)
            val active = host.activePlayer
            val untilSwitch = overlap.switchPointMs - active.currentPosition
            val late = untilSwitch < -LATE_START_MS
            if (!preloaded && untilSwitch <= overlap.overlapMs + config.preloadLeadMs) {
                if (late) {
                    started = false
                } else {
                    preload(player, plan, overlap.incomingStartMs)
                    preloaded = true
                }
            }
            val atSwitch = preloaded && untilSwitch <= 0 && active.isPlaying
            if (started == null && atSwitch) {
                started = !late && player.playbackState == Player.STATE_READY
                if (started) runFade(host, overlapFade(active, player, overlap))
            }
            // Close to the switch point the watcher wakes on it, so the start is not up to one tick late (a late
            // start is a beat-phase error of the same size).
            wait = if (preloaded && untilSwitch in 1..TICK_MS) untilSwitch else TICK_MS
        }
        return started == true
    }

    private fun overlapFade(
        active: ExoPlayer,
        player: ExoPlayer,
        overlap: TransitionPlan.Overlap,
    ) = Fade(
        active,
        player,
        overlap.overlapMs,
        overlap.incomingStartMs,
        overlap.curve,
        speed = overlap.incomingSpeed,
        lock = phaseLockFor(overlap),
        easeBack = overlap.easeBack,
        stretched = overlap.incomingSpeed != 1f,
        bassCurve = overlap.bass,
    )

    /** Phase lock is a switch: off, an overlap runs at its planned speed from start to end (ADR 0096). */
    private fun phaseLockFor(overlap: TransitionPlan.Overlap): PhaseLock? {
        val outgoingGrid = overlap.outgoingGrid
        val incomingGrid = overlap.incomingGrid
        return if (config.phaseLockEnabled && outgoingGrid != null && incomingGrid != null) {
            PhaseLock(config, outgoingGrid, incomingGrid, overlap.incomingSpeed.toDouble())
        } else {
            null
        }
    }

    private suspend fun runFade(
        host: CrossfadeHost,
        running: Fade,
    ) {
        val outgoing = running.retiring
        val incoming = running.incoming
        // An ease-back still running belongs to the player that is the outgoing one now: it goes back to 1.0.
        cancelEase()
        fade = running
        incoming.volume = 0f
        running.bassCurve?.let { applyBass(running, it, 0f) }
        if (running.stretched) incoming.playbackParameters = PlaybackParameters(running.speed)
        incoming.playWhenReady = true
        host.fadeStarted(incoming)
        running.handedOver = true
        // The outgoing player must not move on to the song that the incoming one is already playing.
        outgoing.removeMediaItems(outgoing.currentMediaItemIndex + 1, outgoing.mediaItemCount)
        outgoing.setAudioAttributes(attributes, false)
        incoming.setAudioAttributes(attributes, true)
        ramp(running)
        finish(running)
        host.fadeFinished()
    }

    private suspend fun ramp(running: Fade) {
        var sinceLock = 0L
        while (true) {
            val progress = progressOf(running)
            running.incoming.volume = running.curve.incoming(progress)
            running.retiring.volume = running.curve.outgoing(progress)
            running.bassCurve?.let { applyBass(running, it, progress) }
            val outgoingState = running.retiring.playbackState
            val outgoingOver = outgoingState == Player.STATE_ENDED || outgoingState == Player.STATE_IDLE
            if (progress >= 1f || outgoingOver) return
            sinceLock += RAMP_TICK_MS
            if (sinceLock >= config.phaseLockTickMs) {
                sinceLock = 0L
                lockPhase(running)
            }
            delay(RAMP_TICK_MS)
        }
    }

    /** The shelf gains of both players at [progress]: the incoming starts cut, the swap is mid-overlap (ADR 0100). */
    private fun applyBass(
        running: Fade,
        curve: BassCurve,
        progress: Float,
    ) {
        bass.of(running.incoming).setGainDb(curve.incomingDb(progress))
        bass.of(running.retiring).setGainDb(curve.outgoingDb(progress))
    }

    /** Both players of a running overlap go back to no processing, and the swap stops (ADR 0102). */
    private fun resetBass(running: Fade) {
        running.bassCurve = null
        bass.of(running.incoming).bypass()
        bass.of(running.retiring).bypass()
    }

    /** How much of the overlap has passed, read from the incoming position, which covers speed times the overlap. */
    private fun progressOf(running: Fade): Float =
        (running.incoming.currentPosition - running.startOffsetMs).toFloat() / (running.durationMs * running.speed)

    /** One phase lock tick: nudge the incoming speed, or give the beat match up (ADR 0096). */
    private fun lockPhase(running: Fade) {
        val lock = running.lock ?: return
        val current = running.incoming.playbackParameters.speed
        val action =
            lock.update(
                running.retiring.currentPosition.toDouble(),
                running.incoming.currentPosition.toDouble(),
                current.toDouble(),
            )
        when (action) {
            is PhaseLock.Action.Speed ->
                if (abs(action.value - current) >= MIN_SPEED_CHANGE) {
                    running.incoming.playbackParameters = PlaybackParameters(action.value)
                }
            PhaseLock.Action.Abandon -> abandonMatch(running)
        }
    }

    /** From now on the overlap is an equal-power fade that starts from the gains it has, so nothing jumps. */
    private fun abandonMatch(running: Fade) {
        val progress = progressOf(running).coerceIn(0f, 1f)
        running.curve = EqualPowerTail(progress, running.curve.incoming(progress), running.curve.outgoing(progress))
        running.lock = null
    }

    /**
     * The fade ran its course: the incoming song is at full volume alone, the outgoing player is the spare. A stretched
     * incoming song eases back to speed 1.0 on the plan's schedule; the outgoing player is back at 1.0 already.
     */
    private fun finish(running: Fade) {
        running.incoming.volume = 1f
        bass.of(running.incoming).bypass()
        discard(running.retiring)
        spare = running.retiring
        fade = null
        if (running.stretched) startEase(running.incoming, running.easeBack) else resetSpeed(running.incoming)
    }

    /** Runs [steps] on [player]: each step is set once the player's position has reached it (ADR 0095). */
    private fun startEase(
        player: ExoPlayer,
        steps: List<SpeedStep>,
    ) {
        if (steps.isEmpty()) {
            resetSpeed(player)
            return
        }
        easing = player
        easeJob = scope.launch { ease(player, steps) }
    }

    private suspend fun ease(
        player: ExoPlayer,
        steps: List<SpeedStep>,
    ) {
        var next = 0
        while (next < steps.size) {
            delay(TICK_MS)
            val position = player.currentPosition
            var target: Float? = null
            while (next < steps.size && steps[next].atMs <= position) {
                target = steps[next].speed
                next++
            }
            if (target != null) setSpeed(player, target)
        }
        easing = null
    }

    /** Ends an ease-back that is running and puts its player at speed 1.0. */
    private fun cancelEase() {
        easeJob?.cancel()
        easeJob = null
        easing?.let(::resetSpeed)
        easing = null
    }

    private fun setSpeed(
        player: ExoPlayer,
        speed: Float,
    ) {
        player.playbackParameters = if (speed == 1f) PlaybackParameters.DEFAULT else PlaybackParameters(speed)
    }

    /**
     * A fade is cut short (a jump, a seek, a new queue, new settings, release). Past the hand-over the incoming
     * player is the host's and keeps playing at full volume; before it, the incoming player is dropped and the
     * outgoing one carries on untouched.
     */
    private fun abortFade() {
        val running = fade ?: return
        fade = null
        if (running.handedOver) {
            running.incoming.volume = 1f
            resetSpeed(running.incoming)
            bass.of(running.incoming).bypass()
            discard(running.retiring)
            spare = running.retiring
        } else {
            discard(running.incoming)
            spare = running.incoming
        }
    }

    private fun preload(
        player: ExoPlayer,
        plan: PlannedTransition,
        startMs: Long = 0,
    ) {
        player.volume = 0f
        player.playWhenReady = false
        // The incoming song starts cut: set before the first buffer, and a flush snaps to it (ADR 0101).
        plan.overlap?.bass?.let { bass.of(player).setGainDb(it.incomingDb(0f)) } ?: bass.of(player).bypass()
        if (startMs > 0) player.setMediaItem(plan.incoming, startMs) else player.setMediaItem(plan.incoming)
        player.prepare()
    }

    /** A player that was sped up or slowed goes back to speed 1.0; nothing is allocated when it is already there. */
    private fun resetSpeed(player: ExoPlayer) {
        if (player.playbackParameters.speed != 1f) player.playbackParameters = PlaybackParameters.DEFAULT
    }

    /** Silent, stopped, empty, at full volume's opposite, ready for the next preload. */
    private fun discard(player: ExoPlayer) {
        resetSpeed(player)
        bass.of(player).bypass()
        player.stop()
        player.clearMediaItems()
        player.volume = 0f
        player.playWhenReady = false
        player.setAudioAttributes(attributes, false)
    }

    private fun configureSpare(player: ExoPlayer) {
        // Only the session's player holds audio focus; a second holder would take it from the first.
        player.setAudioAttributes(attributes, false)
        player.setHandleAudioBecomingNoisy(true)
        player.setWakeMode(C.WAKE_MODE_LOCAL)
        player.volume = 0f
    }

    private fun remainingMs(
        active: ExoPlayer,
        plan: PlannedTransition,
    ): Long {
        val duration = active.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: plan.outgoingDurationMs
        return duration - active.currentPosition
    }

    companion object {
        /** How often the watcher looks at the outgoing song's position. */
        const val TICK_MS = 50L

        /** How often the volumes are set during a fade. */
        const val RAMP_TICK_MS = 25L

        /** How long before the fade starts the next song is handed to the spare player. */
        const val PRELOAD_LEAD_MS = 8_000L

        /** An overlap whose switch point is more than this far behind is not started: the phase 6 plan runs. */
        const val LATE_START_MS = 1_000L

        /** Phase lock does not touch the speed for a change smaller than this. */
        const val MIN_SPEED_CHANGE = 0.0005f

        /** A fade shorter than this is not worth the two players; the join stays plain. */
        const val MIN_FADE_MS = 500L

        /** The factory with no bass filter: phase 6 and slice 1 and 2 behaviour; the spare is built when needed. */
        val factory = engineFactory(build = { ExoPlayer.Builder(it).build() })

        /** The Hilt-bound factory with the bass filter: [players] builds the spare, finds each player's control. */
        @OptIn(UnstableApi::class)
        fun factory(players: BassPlayers): CrossfadeEngineFactory = engineFactory({ players.newPlayer(it) }, players)

        private fun engineFactory(
            build: (Context) -> ExoPlayer,
            bass: BassControlSource = BassControlSource.None,
        ) = CrossfadeEngineFactory { context: Context, scope: CoroutineScope ->
            val attributes =
                AudioAttributes
                    .Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build()
            TwoPlayerCrossfadeEngine(scope, { build(context) }, attributes, bass = bass)
        }
    }
}
