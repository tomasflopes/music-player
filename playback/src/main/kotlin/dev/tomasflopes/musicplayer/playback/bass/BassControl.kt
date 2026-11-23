package dev.tomasflopes.musicplayer.playback.bass

import androidx.media3.exoplayer.ExoPlayer

/**
 * The bass filter of one player (design phase 7, slice 3, ADR 0102): a low shelf whose gain the engine sets from the
 * plan, and a bypass in which nothing is processed. A decision point (CLAUDE.md): [ShelfAudioProcessor] is the
 * implementation that is wired, [AudioFxBassControl] is the one to benchmark. Any thread may call it.
 */
interface BassControl {
    /** The gain the filter moves to, in dB, from the full cut up to 0; 0 when flat. */
    val targetDb: Float

    /** Move to [db] (clamped to the cut depth..0), smoothly; NaN is ignored. */
    fun setGainDb(db: Float)

    /** Go flat and, once flat, stop processing. */
    fun bypass()
}

/** True when the filter is asked for no cut. */
val BassControl.isBypassed: Boolean get() = targetDb == 0f

/** The control of a player that has no filter: every call does nothing. */
object NoBassControl : BassControl {
    override val targetDb: Float = 0f

    override fun setGainDb(db: Float) = Unit

    override fun bypass() = Unit
}

/** Finds the [BassControl] of a player; the engine asks it for the outgoing and the incoming player. */
fun interface BassControlSource {
    fun of(player: ExoPlayer): BassControl

    companion object {
        /** Nothing is filtered: phase 6 and slice 1 and 2 behaviour. */
        val None = BassControlSource { NoBassControl }
    }
}
