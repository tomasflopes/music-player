package dev.tomasflopes.musicplayer.core.model.crossfade

import dev.tomasflopes.musicplayer.core.model.AlbumKey
import dev.tomasflopes.musicplayer.core.model.AlbumOrder
import dev.tomasflopes.musicplayer.core.model.Song

/** What made the player leave the outgoing song. Only the natural end of a song can be crossfaded. */
enum class TransitionCause {
    /** The song played to its end, untouched. */
    END_OF_SONG,

    /** The owner moved the position inside the outgoing song before its end (seek bar, fast-forward). */
    MANUAL_SEEK,

    /** The owner chose the next song: next, previous, a tap in a list or the queue, a controller's jump. */
    TAP_JUMP,
}

/** One song-to-song transition, as the policy sees it. */
data class Transition(
    val outgoing: Song,
    val incoming: Song,
    val cause: TransitionCause,
    /** Shuffle is on, so two songs of one album that happen to follow each other are not an album in order. */
    val shuffled: Boolean,
    val repeatOne: Boolean,
)

sealed interface CrossfadeDecision {
    /** Join the songs the plain way: gapless where the files allow it, a cut otherwise. */
    data object HardCut : CrossfadeDecision

    /** Blend the end of the outgoing song with the start of the incoming one over [durationMs]. */
    data class Fade(
        val durationMs: Long,
    ) : CrossfadeDecision
}

/**
 * Decides, for one transition, whether to crossfade and for how long (design phase 6). Pure: no player, no clock.
 *
 * A hard cut when, in this order: crossfade is off; the repeat mode is repeat-one; the transition was not the natural
 * end of the song (a manual seek, or a tap, next or previous); both songs are the same file; the incoming song is the
 * next one of the same album in album order (an album played in order stays gapless); a duration is unknown; or the
 * fade that fits is shorter than [MIN_FADE_MS]. Otherwise a fade of the requested length, shrunk so that neither song
 * is shorter than three fades: `min(requested, shorter song / 3)`.
 */
object CrossfadePolicy {
    /** Below this a fade is just a click, so the transition is cut instead. */
    const val MIN_FADE_MS = 500L

    /** Each song must be at least this many fades long, or the fade shrinks to fit. */
    const val SONG_LENGTH_IN_FADES = 3

    fun decide(
        settings: CrossfadeSettings,
        transition: Transition,
    ): CrossfadeDecision {
        val cut = CrossfadeDecision.HardCut
        return when {
            !settings.enabled -> cut
            transition.repeatOne -> cut
            transition.cause != TransitionCause.END_OF_SONG -> cut
            transition.outgoing.id == transition.incoming.id -> cut
            isAlbumInOrder(transition) -> cut
            else -> fadeThatFits(settings.coerced().durationMs, transition)
        }
    }

    private fun isAlbumInOrder(transition: Transition): Boolean {
        val outgoing = transition.outgoing
        val incoming = transition.incoming
        val album = outgoing.albumKey()
        return !transition.shuffled &&
            album != null &&
            album == incoming.albumKey() &&
            AlbumOrder.compare(outgoing, incoming) < 0
    }

    private fun fadeThatFits(
        requestedMs: Long,
        transition: Transition,
    ): CrossfadeDecision {
        val shorter = minOf(transition.outgoing.durationMs, transition.incoming.durationMs)
        if (shorter <= 0) return CrossfadeDecision.HardCut
        val fade = minOf(requestedMs, shorter / SONG_LENGTH_IN_FADES)
        return if (fade < MIN_FADE_MS) CrossfadeDecision.HardCut else CrossfadeDecision.Fade(fade)
    }

    private fun Song.albumKey() = AlbumKey.of(albumTitle, albumArtist, tagArtistName)
}
