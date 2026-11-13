package dev.tomasflopes.musicplayer.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import dev.tomasflopes.musicplayer.core.model.Song
import dev.tomasflopes.musicplayer.core.model.SongId
import dev.tomasflopes.musicplayer.core.model.SongRef
import dev.tomasflopes.musicplayer.core.model.log.PlaySource
import dev.tomasflopes.musicplayer.core.model.ports.SongReader
import dev.tomasflopes.musicplayer.playback.queue.PlayQueue
import dev.tomasflopes.musicplayer.playback.queue.QueueEntry
import dev.tomasflopes.musicplayer.playback.queue.QueueWindowSync
import dev.tomasflopes.musicplayer.playback.queue.RepeatMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Everything the service does with the queue and the player, on the main thread, one command at a time.
 * The queue decides what plays; the player is kept equal to a window of it by [QueueWindowSync].
 */
internal class PlaybackEngine(
    private val player: ExoPlayer,
    private val queue: PlayQueue,
    private val songs: SongReader,
    private val mapper: MediaItemMapper,
    private val config: PlaybackConfig,
    private val scope: CoroutineScope,
) : Player.Listener {
    private val cache = HashMap<SongId, Song>()
    private val mutex = Mutex()
    private val sync = QueueWindowSync(queue, ExoPlayerItems(), config)

    /** What the session offers to controllers. */
    val exposed = QueueAwarePlayer(player, queue, this)

    init {
        player.addListener(this)
    }

    fun play(request: PlayRequest) =
        serial {
            queue.replace(request.songs, request.startIndex, request.source, request.shuffle)
            prepareWindow()
            sync.onQueueReplaced()
            player.prepare()
            player.play()
            exposed.notifyModes()
        }

    fun playNext(refs: List<SongRef>) = serial { edit { queue.playNext(refs) } }

    fun addToQueue(refs: List<SongRef>) = serial { edit { queue.addToQueue(refs) } }

    fun jumpToUpcoming(index: Int) =
        serial {
            queue.jumpTo(queue.index + 1 + index) ?: return@serial
            prepareWindow()
            sync.fill()
            player.play()
        }

    fun next() =
        serial {
            queue.next() ?: return@serial
            moveTo(forward = true)
        }

    fun previous() =
        serial {
            if (player.currentPosition > config.restartThresholdMs) return@serial player.seekTo(0)
            queue.previous() ?: return@serial player.seekTo(0)
            moveTo(forward = false)
        }

    fun setShuffle(on: Boolean) =
        serial {
            edit { queue.setShuffled(on) }
            exposed.notifyModes()
        }

    fun setRepeat(mode: RepeatMode) =
        serial {
            queue.repeatMode = mode
            player.repeatMode = if (mode == RepeatMode.ONE) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            edit {}
            exposed.notifyModes()
        }

    fun hasNext() = queue.index < queue.size - 1 || (queue.repeatMode != RepeatMode.OFF && queue.size > 0)

    fun hasPrevious() = queue.index > 0 || (queue.repeatMode != RepeatMode.OFF && queue.size > 0)

    fun release() = player.removeListener(this)

    // Player.Listener: the player moved on by itself, finished, or could not open a file.

    override fun onMediaItemTransition(
        mediaItem: MediaItem?,
        reason: Int,
    ) {
        if (reason != Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) return
        serial {
            queue.advanceOnTrackEnd()
            prepareWindow()
            sync.onPlayerAdvanced()
        }
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        if (playbackState != Player.STATE_ENDED || queue.repeatMode != RepeatMode.ALL) return
        serial {
            queue.advanceOnTrackEnd()
            prepareWindow()
            sync.fill()
            player.play()
        }
    }

    override fun onPlayerError(error: PlaybackException) {
        serial {
            val failed = queue.current?.ref ?: return@serial
            prepareWindow()
            if (!sync.onItemFailed(failed)) return@serial player.stop()
            player.prepare()
            player.play()
        }
    }

    /** Edits the queue's order, then brings the part of the player after the current song in line. */
    private suspend fun edit(change: () -> Unit) {
        change()
        prepareWindow()
        sync.onQueueReordered()
    }

    /** The queue already moved by one; slide the player along, or reload when the window does not line up. */
    private suspend fun moveTo(forward: Boolean) {
        prepareWindow()
        val neighbour = player.currentMediaItemIndex + if (forward) 1 else -1
        val wanted =
            queue.current
                ?.ref
                ?.songId
                ?.value
                ?.toString()
        if (neighbour in 0 until player.mediaItemCount && player.getMediaItemAt(neighbour).mediaId == wanted) {
            if (forward) player.seekToNextMediaItem() else player.seekToPreviousMediaItem()
            sync.onPlayerAdvanced()
        } else {
            sync.fill()
        }
        player.play()
    }

    /**
     * Loads the songs the window needs and drops queue entries that have left the library. It reaches one
     * window further ahead than the player holds, so the sync (which cannot wait for the database) always
     * finds the songs of whatever it appends.
     */
    private suspend fun prepareWindow() {
        while (true) {
            val window = queue.window(config.windowBehind, config.windowAhead * 2).map { it.ref }
            val missing = window.filter { it.songId !in cache }
            if (missing.isEmpty()) return
            songs.songsByIds(missing.map { it.songId }.distinct()).forEach { cache[it.id] = it }
            val gone = missing.filter { it.songId !in cache }
            if (gone.isEmpty()) return
            removeFromQueue(gone.toSet())
        }
    }

    private fun removeFromQueue(gone: Set<SongRef>) {
        val entries = queue.snapshot().entries
        entries.indices
            .filter { entries[it].ref in gone }
            .asReversed()
            .forEach { queue.removeAt(it) }
    }

    private fun serial(block: suspend () -> Unit) {
        scope.launch { mutex.withLock { block() } }
    }

    private inner class ExoPlayerItems : QueueWindowSync.PlayerItems {
        override val count get() = player.mediaItemCount
        override val index get() = player.currentMediaItemIndex

        override fun setAll(
            refs: List<SongRef>,
            startIndex: Int,
        ) = player.setMediaItems(refs.map(::item), startIndex, 0L)

        override fun append(refs: List<SongRef>) = player.addMediaItems(refs.map(::item))

        override fun removeRange(
            from: Int,
            to: Int,
        ) = player.removeMediaItems(from, to)

        override fun removeFirst(count: Int) = player.removeMediaItems(0, count)

        private fun item(ref: SongRef): MediaItem {
            val song = checkNotNull(cache[ref.songId]) { "song ${ref.songId.value} was not loaded" }
            return mapper.toMediaItem(QueueEntry(ref, PlaySource.SONGS, manual = false), song)
        }
    }
}
