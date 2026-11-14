package dev.tomasflopes.musicplayer.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import dev.tomasflopes.musicplayer.core.model.AlbumId
import dev.tomasflopes.musicplayer.core.model.ArtistId
import dev.tomasflopes.musicplayer.core.model.Song
import dev.tomasflopes.musicplayer.core.model.SongId
import dev.tomasflopes.musicplayer.core.model.SongRef
import dev.tomasflopes.musicplayer.core.model.log.PlaySource
import dev.tomasflopes.musicplayer.core.model.ports.SongReader
import dev.tomasflopes.musicplayer.playback.log.PlayEventRecorder
import dev.tomasflopes.musicplayer.playback.log.PlayEventTracker
import dev.tomasflopes.musicplayer.playback.log.RecordingHistory
import dev.tomasflopes.musicplayer.playback.queue.PlayQueue
import dev.tomasflopes.musicplayer.playback.queue.QueueEntry
import dev.tomasflopes.musicplayer.playback.queue.ReverseShuffle
import dev.tomasflopes.musicplayer.playback.queue.refs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import kotlin.random.Random

/** Building a real [MediaItem] needs Android's Uri, so the harness hands the player an empty one. */
internal class FakeMapper : MediaItemMapper() {
    override fun toMediaItem(
        entry: QueueEntry,
        song: Song,
    ): MediaItem = MediaItem.EMPTY
}

/** What the fake ExoPlayer reports, and every call it received that changes something. */
internal class FakePlayerState {
    var position = 0L
    var playing = false
    var itemIndex = 0
    var itemCount = 0
    var listener: Player.Listener? = null
    val calls = mutableListOf<String>()
}

/**
 * A [PlaybackEngine] on the JVM: ExoPlayer is a recording proxy (it needs Android otherwise), the queue and the
 * recorder are the real ones. A row is started by firing the repeat-one transition the real player would send.
 */
internal class EngineHarness(
    queueNames: String,
    mapper: MediaItemMapper = FakeMapper(),
) {
    var time = 1_000L
    val state = FakePlayerState()
    val history = RecordingHistory()
    val queue = PlayQueue(ReverseShuffle, Random(1))
    private val recorder =
        PlayEventRecorder(
            history,
            CoroutineScope(Dispatchers.Unconfined),
            tracker = PlayEventTracker({ time }, { time }),
        )
    val engine =
        PlaybackEngine(
            fakeExoPlayer(state),
            queue,
            songs(),
            mapper,
            PlaybackConfig(),
            CoroutineScope(Dispatchers.Unconfined),
            recorder,
        )

    init {
        queue.replace(refs(queueNames), 0, PlaySource.ALBUM, false)
        state.itemCount = queueNames.length
    }

    /** The first song starts playing, as it would after a play request. */
    fun startPlaying() {
        state.listener?.onMediaItemTransition(null, Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT)
        state.playing = true
        state.listener?.onIsPlayingChanged(true)
    }

    private fun songs() =
        object : SongReader {
            override fun observeSongs(): Flow<List<Song>> = emptyFlow()

            override fun observeAddedSince(epochSec: Long): Flow<List<Song>> = emptyFlow()

            override fun observeAlbumSongs(id: AlbumId): Flow<List<Song>> = emptyFlow()

            override fun observeArtistSongs(id: ArtistId): Flow<List<Song>> = emptyFlow()

            override fun observeFeaturedOn(id: ArtistId): Flow<List<Song>> = emptyFlow()

            override fun observeFolderSongs(folderPath: String): Flow<List<Song>> = emptyFlow()

            override suspend fun songsByIds(ids: List<SongId>) = ids.map { song(it.value) }

            override suspend fun allSongRefs(): List<SongRef> = emptyList()
        }
}

private fun fakeExoPlayer(state: FakePlayerState): ExoPlayer =
    Proxy.newProxyInstance(
        ExoPlayer::class.java.classLoader,
        arrayOf(ExoPlayer::class.java),
        FakePlayerHandler(state),
    ) as ExoPlayer

/** Answers the few questions the engine asks; every other call is recorded and answered with a zero. */
private class FakePlayerHandler(
    private val state: FakePlayerState,
) : InvocationHandler {
    override fun invoke(
        proxy: Any,
        method: Method,
        args: Array<Any?>?,
    ): Any? =
        when (method.name) {
            "getCurrentPosition" -> state.position
            "isPlaying" -> state.playing
            "getCurrentMediaItemIndex" -> state.itemIndex
            "getMediaItemCount" -> state.itemCount
            "hashCode" -> System.identityHashCode(proxy)
            "equals" -> proxy === args?.get(0)
            "toString" -> "FakeExoPlayer"
            else -> record(method, args?.toList().orEmpty())
        }

    private fun record(
        method: Method,
        args: List<Any?>,
    ): Any? {
        if (method.name == "addListener") state.listener = args[0] as Player.Listener
        state.calls += method.name + args
        if (method.name == "seekTo" && args.size == 2) state.itemIndex = args[0] as Int
        return when (method.returnType) {
            Int::class.javaPrimitiveType -> 0
            Long::class.javaPrimitiveType -> 0L
            Boolean::class.javaPrimitiveType -> false
            Float::class.javaPrimitiveType -> 0f
            else -> null
        }
    }
}
