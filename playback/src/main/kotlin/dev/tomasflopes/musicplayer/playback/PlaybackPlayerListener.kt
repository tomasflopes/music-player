package dev.tomasflopes.musicplayer.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player

internal class PlaybackPlayerListener(
    private val serial: (suspend () -> Unit) -> Unit,
    private val onAutoAdvance: suspend () -> Unit,
    private val onRepeat: () -> Unit,
    private val onEnded: suspend () -> Unit,
    private val onError: suspend (PlaybackException) -> Unit,
    private val onPlayingChanged: (Boolean) -> Unit,
) : Player.Listener {
    override fun onMediaItemTransition(
        mediaItem: MediaItem?,
        reason: Int,
    ) {
        when (reason) {
            Player.MEDIA_ITEM_TRANSITION_REASON_AUTO -> serial { onAutoAdvance() }
            Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT -> serial { onRepeat() }
        }
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        if (playbackState == Player.STATE_ENDED) serial { onEnded() }
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) = onPlayingChanged(isPlaying)

    override fun onPlayerError(error: PlaybackException) {
        serial { onError(error) }
    }
}
