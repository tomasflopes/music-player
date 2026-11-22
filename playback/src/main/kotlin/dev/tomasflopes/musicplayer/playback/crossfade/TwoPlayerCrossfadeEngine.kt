package dev.tomasflopes.musicplayer.playback.crossfade

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import dev.tomasflopes.musicplayer.core.model.crossfade.CrossfadeDecision
import dev.tomasflopes.musicplayer.core.model.transition.EqualPowerCurve
import dev.tomasflopes.musicplayer.core.model.transition.GainCurve
import dev.tomasflopes.musicplayer.core.model.transition.TransitionConfig
import dev.tomasflopes.musicplayer.core.model.transition.TransitionPlan
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
 * not ready, or whose switch point has passed, runs the phase 6 plan instead. Whenever a fade ends, is cut or paused,
 * both players go back to speed 1.0.
 */
class TwoPlayerCrossfadeEngine(
    private val scope: CoroutineScope,
    private val secondary: () -> ExoPlayer,
    private val attributes: AudioAttributes,
    private val config: TransitionConfig = TransitionConfig(),
) : CrossfadeEngine {
    private class Fade(
        val retiring: ExoPlayer,
        val incoming: ExoPlayer,
        val durationMs: Long,
        /** Where in its own file the incoming song starts; its position counts from here. */
        val startOffsetMs: Long = 0,
        val curve: GainCurve = EqualPowerCurve,
        /** Set once the host took [incoming] as its player; before that a cancelled fade leaves the host untouched. */
        var handedOver: Boolean = false,
    )

    private var host: CrossfadeHost? = null
    private var spare: ExoPlayer? = null
    private var fade: Fade? = null
    private var job: Job? = null

    override fun attach(host: CrossfadeHost) {
        this.host = host
    }

    override fun invalidate() {
        abortFade()
        job?.cancel()
        spare?.let(::discard)
        job = scope.launch { watch() }
    }

    override fun onPlayingChanged(playing: Boolean) {
        val running = fade?.takeIf { it.handedOver } ?: return
        running.retiring.playWhenReady = playing
        if (!playing) {
            resetSpeed(running.retiring)
            resetSpeed(running.incoming)
        }
    }

    override fun release() {
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
        while (started == null) {
            delay(TICK_MS)
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
                if (started) {
                    val running = Fade(active, player, overlap.overlapMs, overlap.incomingStartMs, overlap.curve)
                    runFade(host, running, overlap.incomingSpeed)
                }
            }
        }
        return started == true
    }

    private suspend fun runFade(
        host: CrossfadeHost,
        running: Fade,
        speed: Float = 1f,
    ) {
        val outgoing = running.retiring
        val incoming = running.incoming
        fade = running
        incoming.volume = 0f
        if (speed != 1f) incoming.playbackParameters = PlaybackParameters(speed)
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
        while (true) {
            val progress = (running.incoming.currentPosition - running.startOffsetMs).toFloat() / running.durationMs
            running.incoming.volume = running.curve.incoming(progress)
            running.retiring.volume = running.curve.outgoing(progress)
            val outgoingState = running.retiring.playbackState
            val outgoingOver = outgoingState == Player.STATE_ENDED || outgoingState == Player.STATE_IDLE
            if (progress >= 1f || outgoingOver) return
            delay(RAMP_TICK_MS)
        }
    }

    /** The fade ran its course: the incoming song is at full volume alone, the outgoing player is the spare. */
    private fun finish(running: Fade) {
        running.incoming.volume = 1f
        resetSpeed(running.incoming)
        discard(running.retiring)
        spare = running.retiring
        fade = null
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

        /** A fade shorter than this is not worth the two players; the join stays plain. */
        const val MIN_FADE_MS = 500L

        /** The Hilt-bound factory; the second ExoPlayer is built the first time a fade needs it. */
        val factory =
            CrossfadeEngineFactory { context: Context, scope: CoroutineScope ->
                val attributes =
                    AudioAttributes
                        .Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .build()
                TwoPlayerCrossfadeEngine(scope, { ExoPlayer.Builder(context).build() }, attributes)
            }
    }
}
