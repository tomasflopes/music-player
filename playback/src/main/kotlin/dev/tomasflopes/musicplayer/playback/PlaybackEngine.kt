package dev.tomasflopes.musicplayer.playback

import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import dev.tomasflopes.musicplayer.core.model.Song
import dev.tomasflopes.musicplayer.core.model.SongId
import dev.tomasflopes.musicplayer.core.model.SongRef
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
) {
    private val cache = HashMap<SongId, Song>()
    private val mutex = Mutex()
    private val sync = QueueWindowSync(queue, ExoPlayerItems(player, cache, mapper), config)
    private val log = PlaybackLog(recorder, queue, { cache[it]?.durationMs ?: 0L }, { player.isPlaying })
    private val playerEvents =
        PlaybackPlayerListener(
            serial = ::serial,
            onAutoAdvance = {
                log.completed()
                queue.advanceOnTrackEnd()
                prepareWindow()
                sync.onPlayerAdvanced()
                log.started(StartedBy.AUTO)
                saving.changed()
            },
            onRepeat = {
                log.completed()
                log.started(StartedBy.AUTO)
            },
            onEnded = {
                log.completed()
                if (queue.repeatMode == RepeatMode.ALL) {
                    queue.advanceOnTrackEnd()
                    prepareWindow()
                    sync.fill()
                    player.play()
                    log.started(StartedBy.AUTO)
                    saving.changed()
                }
            },
            onError = {
                queue.current?.ref?.let { failed ->
                    log.stopped()
                    prepareWindow()
                    if (!sync.onItemFailed(failed)) {
                        player.stop()
                    } else {
                        player.prepare()
                        player.play()
                        log.started(StartedBy.AUTO)
                        saving.changed()
                    }
                }
            },
            onPlayingChanged = {
                log.playingChanged(it)
                ticker?.cancel()
                if (it) {
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
            },
        )

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

    /** Jumps to any entry of the queue, played or upcoming; the queue itself is not changed (ADR 0047). */
    fun jumpToQueueIndex(
        index: Int,
        songId: SongId,
    ) = serial {
        if (queue.entryAt(index)?.ref?.songId != songId) return@serial
        queue.jumpTo(index) ?: return@serial
        prepareWindow()
        sync.fill()
        player.prepare()
        player.play()
        log.started(StartedBy.PICKED)
        exposed.notifyModes()
        saving.changed()
    }

    /**
     * Takes one entry out of the queue (ADR 0047). Any entry but the current one is swapped out of the window
     * without touching the song that plays. Removing the current song ends its row, and the next song takes its
     * place (playing if the player was); with no next song, playback stops on the one before it.
     */
    fun removeFromQueue(
        index: Int,
        songId: SongId,
    ) = serial {
        if (queue.entryAt(index)?.ref?.songId != songId) return@serial
        if (index != queue.index) {
            queue.remove(index)
            prepareWindow()
            sync.onQueueEdited()
        } else {
            val playing = player.playWhenReady
            log.stopped()
            queue.remove(index)
            prepareWindow()
            sync.fill()
            val hasNext = queue.size > 0 && index < queue.size
            if (playing && hasNext) {
                player.prepare()
                player.play()
                log.started(StartedBy.NAV)
            } else {
                player.pause()
                log.awaitFirstPlay()
            }
        }
        exposed.notifyModes()
        saving.changed()
    }

    /** Puts a removed entry back (undo). A removed current song becomes current again, at its position. */
    fun restoreToQueue(removed: RemovedQueueEntry) =
        serial {
            queue.insertAt(removed.index, QueueEntry(removed.ref, removed.source, removed.manual, removed.playlistId))
            prepareWindow()
            if (removed.wasCurrent) {
                queue.jumpTo(removed.index.coerceAtMost(queue.size - 1)) ?: return@serial
                sync.fill()
                player.prepare()
                player.seekTo(removed.positionMs)
                if (removed.wasPlaying) {
                    player.play()
                    log.started(StartedBy.NAV)
                } else {
                    player.pause()
                    log.awaitFirstPlay()
                }
            } else {
                sync.onQueueEdited()
            }
            exposed.notifyModes()
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
            if (player.currentPosition > config.restartThresholdMs) {
                player.seekTo(0)
                log.restarted()
                return@serial
            }
            if (queue.previous() == null) {
                player.seekTo(0)
                log.restarted()
                return@serial
            }
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
            val entries = queue.snapshot().entries
            entries.indices
                .filter { entries[it].ref in gone }
                .asReversed()
                .forEach { queue.removeAt(it) }
            saving.changed()
        }
    }

    private fun serial(block: suspend () -> Unit) {
        scope.launch { mutex.withLock { block() } }
    }
}
