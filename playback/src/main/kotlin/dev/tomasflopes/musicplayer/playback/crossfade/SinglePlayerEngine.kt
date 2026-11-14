package dev.tomasflopes.musicplayer.playback.crossfade

import android.content.Context
import kotlinx.coroutines.CoroutineScope

/**
 * The phase 1 to 5 path: one ExoPlayer, songs joined by Media3's own gapless transition, never a fade. It does
 * nothing because the player already does everything; it exists so the two-player engine has an alternative to be
 * measured against (CLAUDE.md, decision points; ADR 0061).
 */
object SinglePlayerEngine : CrossfadeEngine {
    override fun attach(host: CrossfadeHost) = Unit

    override fun invalidate() = Unit

    override fun onPlayingChanged(playing: Boolean) = Unit

    override fun release() = Unit

    val factory =
        CrossfadeEngineFactory { _: Context, _: CoroutineScope -> SinglePlayerEngine }
}
