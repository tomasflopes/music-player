package dev.tomasflopes.musicplayer.playback

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import dev.tomasflopes.musicplayer.core.model.Song
import dev.tomasflopes.musicplayer.core.model.SongId
import dev.tomasflopes.musicplayer.core.model.SongRef
import dev.tomasflopes.musicplayer.core.model.log.PlaySource
import dev.tomasflopes.musicplayer.core.model.log.StartedBy
import dev.tomasflopes.musicplayer.core.model.ports.SongReader
import dev.tomasflopes.musicplayer.playback.log.PlayEventRecorder
import dev.tomasflopes.musicplayer.playback.log.PlaybackLog
import dev.tomasflopes.musicplayer.playback.queue.PlayQueue
import dev.tomasflopes.musicplayer.playback.queue.QueueEntry
import dev.tomasflopes.musicplayer.playback.queue.QueueRestorer
import dev.tomasflopes.musicplayer.playback.queue.QueueSaving
import dev.tomasflopes.musicplayer.playback.queue.QueueStore
import dev.tomasflopes.musicplayer.playback.queue.QueueWindowSync
import dev.tomasflopes.musicplayer.playback.queue.RepeatMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    recorder: PlayEventRecorder,
    private val store: QueueStore,
    private val saving: QueueSaving,
) : Player.Listener {
    private val cache = HashMap<SongId, Song>()
    private val mutex = Mutex()
    private val sync = QueueWindowSync(queue, ExoPlayerItems(), config)
    private val log = PlaybackLog(recorder, queue, { cache[it]?.durationMs ?: 0L }, { player.isPlaying })
    private val playerEvents = PlayerEvents()

    private var ticker: Job? = null

    /** What the session offers to controllers. */
    val exposed = QueueAwarePlayer(player, queue, this)

    init {
        player.addListener(playerEvents)
    }

    fun play(request: PlayRequest) =
        serial {
            // A shuffle nobody picked a song for starts on a random song, not on the first of the list.
            val randomStart = request.shuffle && !request.picked
            queue.replace(
                request.songs,
                request.startIndex,
                request.source,
                request.shuffle,
                randomStart,
                request.playlistId,
            )
            prepareWindow()
            sync.onQueueReplaced()
            player.prepare()
            player.play()
            log.started(if (request.picked) StartedBy.PICKED else StartedBy.AUTO)
            exposed.notifyModes()
            saving.changed()
        }

    /**
     * Rebuilds the queue the process lost: paused at the saved position, and no log row until the owner plays
     * (ADR 0036). A queue that already exists is never replaced. The job ends when the restore is done.
     */
    fun restore(): Job =
        scope.launch {
            mutex.withLock {
                val saved = if (queue.size == 0) QueueRestorer.load(store, songs) else null
                if (saved != null) {
                    queue.restore(saved.snapshot)
                    prepareWindow()
                    player.repeatMode =
                        if (queue.repeatMode == RepeatMode.ONE) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
                    sync.fill()
                    player.prepare()
                    player.seekTo(saved.positionMs)
                    player.pause()
                    log.awaitFirstPlay()
                    exposed.notifyModes()
                    saving.changed()
                }
            }
        }

    fun playNext(refs: List<SongRef>) = serial { edit { queue.playNext(refs) } }

    fun addToQueue(refs: List<SongRef>) = serial { edit { queue.addToQueue(refs) } }

    fun jumpToUpcoming(index: Int) =
        serial {
            queue.jumpTo(queue.index + 1 + index) ?: return@serial
            prepareWindow()
            sync.fill()
            player.play()
            log.started(StartedBy.PICKED)
            saving.changed()
        }

    /**
     * A controller (notification, car, headset) asked the player for a window index. A window index is a queue entry
     * in the window around the current song, so it goes through the queue as a jump and is logged as picked
     * (ADR 0032). Seeking inside the current song stays a plain seek.
     */
    fun seekToWindow(
        windowIndex: Int,
        positionMs: Long,
    ) = serial {
        val delta = windowIndex - player.currentMediaItemIndex
        if (delta == 0) {
            if (positionMs == C.TIME_UNSET) {
                player.seekToDefaultPosition(windowIndex)
            } else {
                player.seekTo(windowIndex, positionMs)
            }
            return@serial
        }
        queue.jumpTo(queue.index + delta) ?: return@serial
        prepareWindow()
        sync.fill()
        if (positionMs > 0 && positionMs != C.TIME_UNSET) player.seekTo(positionMs)
        log.started(StartedBy.PICKED)
        saving.changed()
    }

    fun next() =
        serial {
            queue.next() ?: return@serial
            moveTo(forward = true)
        }

    fun previous() =
        serial {
            if (player.currentPosition > config.restartThresholdMs) return@serial restart()
            queue.previous() ?: return@serial restart()
            moveTo(forward = false)
        }

    /** Previous that stays on the song: the row so far ends as stopped and the replay is a new nav row (ADR 0033). */
    private fun restart() {
        player.seekTo(0)
        log.restarted()
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

    fun release() {
        ticker?.cancel()
        log.stopped()
        saving.saveBlocking()
        player.removeListener(playerEvents)
    }

    /** Edits the queue's order, then brings the part of the player after the current song in line. */
    private suspend fun edit(change: () -> Unit) {
        change()
        prepareWindow()
        sync.onQueueReordered()
        saving.changed()
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
        log.started(StartedBy.NAV)
        saving.changed()
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
        saving.changed()
    }

    private fun serial(block: suspend () -> Unit) {
        scope.launch { mutex.withLock { block() } }
    }

    private inner class PlayerEvents : Player.Listener {
        override fun onMediaItemTransition(
            mediaItem: MediaItem?,
            reason: Int,
        ) {
            when (reason) {
                Player.MEDIA_ITEM_TRANSITION_REASON_AUTO -> serial { advanceByItself() }
                // Repeat one: the same song starts over, which is a new play (ADR 0025).
                Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT ->
                    serial {
                        log.completed()
                        log.started(StartedBy.AUTO)
                    }
            }
        }

        private suspend fun advanceByItself() {
            log.completed()
            queue.advanceOnTrackEnd()
            prepareWindow()
            sync.onPlayerAdvanced()
            log.started(StartedBy.AUTO)
            saving.changed()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState != Player.STATE_ENDED) return
            serial {
                // The last song of the queue ends here; no item transition reports it.
                log.completed()
                if (queue.repeatMode != RepeatMode.ALL) return@serial
                queue.advanceOnTrackEnd()
                prepareWindow()
                sync.fill()
                player.play()
                log.started(StartedBy.AUTO)
                saving.changed()
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            log.playingChanged(isPlaying)
            ticker?.cancel()
            if (isPlaying) {
                ticker =
                    scope.launch {
                        while (true) {
                            delay(config.positionSaveIntervalMs)
                            saving.saveNow()
                        }
                    }
            } else {
                saving.saveNow()
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            serial {
                val failed = queue.current?.ref ?: return@serial
                log.stopped()
                prepareWindow()
                if (!sync.onItemFailed(failed)) return@serial player.stop()
                player.prepare()
                player.play()
                log.started(StartedBy.AUTO)
                saving.changed()
            }
        }
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
