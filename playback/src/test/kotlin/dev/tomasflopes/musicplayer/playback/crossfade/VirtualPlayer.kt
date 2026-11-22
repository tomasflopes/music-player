package dev.tomasflopes.musicplayer.playback.crossfade

import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import kotlin.math.min

/**
 * An ExoPlayer on a fake clock (ADR 0092): [now] is virtual time in ms, the position advances with it at [speed] while
 * the player is playing, and every call that changes something is kept in [calls]. It needs no Android. A player that
 * is not [ready] never leaves the buffering state, which is how a late preload is simulated.
 */
internal class VirtualPlayer(
    private val now: () -> Long,
    var durationMs: Long = 60_000L,
) {
    var playWhenReady = false
        private set
    var volume = 1f
        private set
    var speed = 1f
        private set
    var ready = true
    var hasItem = false
        private set
    val calls = mutableListOf<String>()
    private var basePosition = 0L
    private var baseTime = 0L

    val position: Long
        get() {
            if (!playWhenReady || !hasItem || !ready) return basePosition
            return min(durationMs, basePosition + ((now() - baseTime) * speed).toLong())
        }

    val state: Int
        get() =
            when {
                !hasItem -> Player.STATE_IDLE
                !ready -> Player.STATE_BUFFERING
                position >= durationMs -> Player.STATE_ENDED
                else -> Player.STATE_READY
            }

    /** Starts a song at [startMs] as if it was loaded already. */
    fun load(startMs: Long = 0L) {
        hasItem = true
        basePosition = startMs
        baseTime = now()
    }

    fun play() = setPlayWhenReady(true)

    val player: ExoPlayer =
        Proxy.newProxyInstance(
            ExoPlayer::class.java.classLoader,
            arrayOf(ExoPlayer::class.java),
            Handler(),
        ) as ExoPlayer

    private fun rebase() {
        basePosition = position
        baseTime = now()
    }

    private fun setPlayWhenReady(value: Boolean) {
        rebase()
        playWhenReady = value
    }

    private inner class Handler : InvocationHandler {
        override fun invoke(
            proxy: Any,
            method: Method,
            args: Array<Any?>?,
        ): Any? {
            val list = args?.toList().orEmpty()
            return when (method.name) {
                "getCurrentPosition" -> position
                "getDuration" -> durationMs
                "getPlaybackState" -> state
                "isPlaying" -> playWhenReady && state == Player.STATE_READY
                "getPlayWhenReady" -> playWhenReady
                "setPlayWhenReady" -> record(method, list) { setPlayWhenReady(list[0] as Boolean) }
                "play" -> record(method, list) { setPlayWhenReady(true) }
                "pause" -> record(method, list) { setPlayWhenReady(false) }
                "getVolume" -> volume
                "setVolume" -> record(method, list) { volume = list[0] as Float }
                "getPlaybackParameters" -> if (speed == 1f) PlaybackParameters.DEFAULT else PlaybackParameters(speed)
                "setPlaybackParameters" ->
                    record(method, list) {
                        rebase()
                        speed = (list[0] as PlaybackParameters).speed
                    }
                "setMediaItem" ->
                    record(method, list) {
                        hasItem = true
                        basePosition = list.getOrNull(1) as? Long ?: 0L
                        baseTime = now()
                    }
                "clearMediaItems" ->
                    record(method, list) {
                        rebase()
                        hasItem = false
                        basePosition = 0L
                    }
                "getMediaItemCount" -> if (hasItem) 1 else 0
                "getCurrentMediaItemIndex" -> 0
                "getMediaItemAt" -> MediaItem.EMPTY
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === list[0]
                "toString" -> "VirtualPlayer"
                else -> record(method, list) {}
            }
        }

        private fun record(
            method: Method,
            args: List<Any?>,
            change: () -> Unit,
        ): Any? {
            change()
            calls += method.name + args
            return when (method.returnType) {
                Int::class.javaPrimitiveType -> 0
                Long::class.javaPrimitiveType -> 0L
                Boolean::class.javaPrimitiveType -> false
                Float::class.javaPrimitiveType -> 0f
                else -> null
            }
        }
    }
}
