package dev.tomasflopes.musicplayer.playback.crossfade

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import dev.tomasflopes.musicplayer.core.model.crossfade.CrossfadeDecision
import dev.tomasflopes.musicplayer.core.model.transition.TransitionPlan
import kotlinx.coroutines.CoroutineScope

/**
 * What follows the current song and how to join the two (ADR 0062, 0092). [decision] is the phase 6 decision. A DJ
 * [overlap] (phase 7) replaces it: the songs play together from [TransitionPlan.Overlap.switchPointMs], and [decision]
 * then holds the overlap as a fade of the same length so a reader that only knows phase 6 still sees a fade. When the
 * overlap cannot run (a late preload), [fallback] is what runs instead: the phase 6 decision.
 */
class PlannedTransition(
    /** The next song, ready for a player. */
    val incoming: MediaItem,
    val decision: CrossfadeDecision,
    /** The outgoing song's length from the library, for when the player does not know its own yet. */
    val outgoingDurationMs: Long,
    val overlap: TransitionPlan.Overlap? = null,
    val fallback: CrossfadeDecision = decision,
) {
    /** The same transition as phase 6 would make it, without the overlap. */
    fun phaseSix() = PlannedTransition(incoming, fallback, outgoingDurationMs)
}

/**
 * The side of the playback engine that a [CrossfadeEngine] talks to: it owns the queue, the log, the session player
 * and the window, so a fade only ever asks it to move. Every call runs on the main thread.
 */
interface CrossfadeHost {
    /** The player the session shows and the queue window lives on; it changes at every fade. */
    val activePlayer: ExoPlayer

    /** The transition after the current song, with the policy's decision; null when nothing follows. */
    suspend fun planNext(): PlannedTransition?

    /**
     * The fade starts now and [incoming] is already playing, silent, the next song from 0:00. The queue moves to it,
     * the outgoing log row ends, the incoming one starts, [incoming] becomes the session's player and holds the
     * window from here on.
     */
    suspend fun fadeStarted(incoming: ExoPlayer)

    /** The fade is over: the incoming song's listening clock starts counting. */
    suspend fun fadeFinished()
}

/**
 * How the end of one song meets the start of the next. A decision point (CLAUDE.md): [SinglePlayerEngine] is the
 * Media3 path of phases 1 to 5, gapless on one ExoPlayer; [TwoPlayerCrossfadeEngine] blends two ExoPlayers by hand.
 * Both are driven the same way, so either can be bound and benchmarked.
 */
interface CrossfadeEngine {
    fun attach(host: CrossfadeHost)

    /**
     * Something about the current or the next song changed (a song started, the queue was edited, a seek, new
     * settings): forget the plan and make a new one. A fade that is running is cut short, hard.
     */
    fun invalidate()

    /** The session player started or stopped playing; a fade in progress follows it. */
    fun onPlayingChanged(playing: Boolean)

    fun release()
}

/** Builds the engine the service uses; the Hilt binding in `PlaybackModule` is the one line that picks it. */
fun interface CrossfadeEngineFactory {
    fun create(
        context: Context,
        scope: CoroutineScope,
    ): CrossfadeEngine
}
