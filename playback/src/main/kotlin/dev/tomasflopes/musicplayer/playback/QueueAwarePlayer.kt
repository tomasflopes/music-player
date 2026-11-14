package dev.tomasflopes.musicplayer.playback

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import dev.tomasflopes.musicplayer.playback.queue.PlayQueue
import dev.tomasflopes.musicplayer.playback.queue.RepeatMode
import java.util.concurrent.CopyOnWriteArraySet

/**
 * What the session shows to every controller (notification, car, headset, the app): transport goes to the
 * real player, but next/previous/shuffle/repeat are answered by the [PlayQueue], which owns the order (A6).
 */
@OptIn(UnstableApi::class)
internal class QueueAwarePlayer(
    player: Player,
    private val queue: PlayQueue,
    private val engine: PlaybackEngine,
) : ForwardingPlayer(player) {
    private val listeners = CopyOnWriteArraySet<Player.Listener>()

    override fun addListener(listener: Player.Listener) {
        listeners += listener
        super.addListener(listener)
    }

    override fun removeListener(listener: Player.Listener) {
        listeners -= listener
        super.removeListener(listener)
    }

    /** The wrapped player knows nothing about the queue's modes, so the changes are announced from here. */
    fun notifyModes() {
        listeners.forEach {
            it.onShuffleModeEnabledChanged(shuffleModeEnabled)
            it.onRepeatModeChanged(repeatMode)
        }
    }

    override fun getShuffleModeEnabled() = queue.shuffled

    override fun setShuffleModeEnabled(shuffleModeEnabled: Boolean) = engine.setShuffle(shuffleModeEnabled)

    override fun getRepeatMode() =
        when (queue.repeatMode) {
            RepeatMode.OFF -> Player.REPEAT_MODE_OFF
            RepeatMode.ALL -> Player.REPEAT_MODE_ALL
            RepeatMode.ONE -> Player.REPEAT_MODE_ONE
        }

    override fun setRepeatMode(repeatMode: Int) =
        engine.setRepeat(
            when (repeatMode) {
                Player.REPEAT_MODE_ALL -> RepeatMode.ALL
                Player.REPEAT_MODE_ONE -> RepeatMode.ONE
                else -> RepeatMode.OFF
            },
        )

    override fun seekToNext() = engine.next()

    override fun seekToNextMediaItem() = engine.next()

    override fun seekToPrevious() = engine.previous()

    override fun seekToPreviousMediaItem() = engine.previous()

    override fun seekTo(
        mediaItemIndex: Int,
        positionMs: Long,
    ) = engine.seekToWindow(mediaItemIndex, positionMs)

    override fun seekToDefaultPosition(mediaItemIndex: Int) = engine.seekToWindow(mediaItemIndex, C.TIME_UNSET)

    override fun hasNextMediaItem() = engine.hasNext()

    override fun hasPreviousMediaItem() = engine.hasPrevious()

    override fun getAvailableCommands(): Player.Commands =
        super
            .getAvailableCommands()
            .buildUpon()
            .addAll(
                Player.COMMAND_SET_SHUFFLE_MODE,
                Player.COMMAND_SET_REPEAT_MODE,
                Player.COMMAND_SEEK_TO_NEXT,
                Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                Player.COMMAND_SEEK_TO_PREVIOUS,
                Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
            ).build()

    override fun isCommandAvailable(command: Int) = availableCommands.contains(command)
}
