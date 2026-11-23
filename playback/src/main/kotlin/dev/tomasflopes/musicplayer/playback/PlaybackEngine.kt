package dev.tomasflopes.musicplayer.playback

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import dev.tomasflopes.musicplayer.core.model.Song
import dev.tomasflopes.musicplayer.core.model.SongId
import dev.tomasflopes.musicplayer.core.model.SongRef
import dev.tomasflopes.musicplayer.core.model.crossfade.CrossfadeDecision
import dev.tomasflopes.musicplayer.core.model.crossfade.CrossfadePolicy
import dev.tomasflopes.musicplayer.core.model.crossfade.CrossfadeSettings
import dev.tomasflopes.musicplayer.core.model.crossfade.Transition
import dev.tomasflopes.musicplayer.core.model.crossfade.TransitionCause
import dev.tomasflopes.musicplayer.core.model.crossfade.TransitionStyle
import dev.tomasflopes.musicplayer.core.model.log.PlaySource
import dev.tomasflopes.musicplayer.core.model.log.StartedBy
import dev.tomasflopes.musicplayer.core.model.ports.SongReader
import dev.tomasflopes.musicplayer.core.model.queue.RefillSeed
import dev.tomasflopes.musicplayer.core.model.transition.TransitionPlan
import dev.tomasflopes.musicplayer.core.model.transition.TransitionPlanner
import dev.tomasflopes.musicplayer.core.model.transition.TransitionProfileSource
import dev.tomasflopes.musicplayer.core.model.transition.TransitionTally
import dev.tomasflopes.musicplayer.playback.crossfade.CrossfadeEngine
import dev.tomasflopes.musicplayer.playback.crossfade.CrossfadeHost
import dev.tomasflopes.musicplayer.playback.crossfade.PlannedTransition
import dev.tomasflopes.musicplayer.playback.crossfade.SinglePlayerEngine
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Everything the service does with the queue and the player, on the main thread, one command at a time.
 * The queue decides what plays; the player is kept equal to a window of it by [QueueWindowSync].
 *
 * It is also the [CrossfadeHost]: with crossfade on, the player that plays changes at every fade (ADR 0061), so
 * [player], the window sync and the session's view of it are swapped at that moment, and nothing else knows.
 */
@Suppress("TooManyFunctions")
internal class PlaybackEngine(
    initialPlayer: ExoPlayer,
    private val queue: PlayQueue,
    private val songs: SongReader,
    private val mapper: MediaItemMapper,
    private val config: PlaybackConfig,
    private val scope: CoroutineScope,
    recorder: PlayEventRecorder,
    private val store: QueueStore,
    private val saving: QueueSaving,
    private val crossfade: CrossfadeEngine = SinglePlayerEngine,
    private val crossfadeSettings: () -> CrossfadeSettings = { CrossfadeSettings() },
    /** The session's player changed: the service hands the new one to the session. */
    private val onPlayerChanged: (QueueAwarePlayer) -> Unit = {},
    /** Chooses the technique per transition (ADR 0092); with no technique registered it is exactly phase 6. */
    private val planner: TransitionPlanner = TransitionPlanner(emptyList()),
    private val profiles: TransitionProfileSource = TransitionProfileSource { null },
    /** Counts the hard cuts of the DJ style for the debug tally (ADR 0112); the planner counts the rest. */
    private val tally: TransitionTally? = null,
) : CrossfadeHost {
    private var player: ExoPlayer = initialPlayer
    private val cache = HashMap<SongId, Song>()
    private val mutex = Mutex()
    private var sync = QueueWindowSync(queue, ExoPlayerItems(initialPlayer, cache, mapper), config)

    /** The owner moved the position inside the current song: its end is a hard cut (crossfade policy). */
    private var seeked = false
    private val log = PlaybackLog(recorder, queue, { cache[it]?.durationMs ?: 0L }, { player.isPlaying })
    private val playerEvents =
        PlaybackPlayerListener(
            serial = ::serial,
            onAutoAdvance = {
                log.completed()
                queue.advanceOnTrackEnd()
                prepareWindow()
                sync.onPlayerAdvanced()
                trackStarted(StartedBy.AUTO)
                queueChanged()
            },
            onRepeat = {
                log.completed()
                trackStarted(StartedBy.AUTO)
            },
            onEnded = {
                log.completed()
                if (queue.repeatMode == RepeatMode.ALL) {
                    queue.advanceOnTrackEnd()
                    prepareWindow()
                    sync.fill()
                    player.play()
                    trackStarted(StartedBy.AUTO)
                    queueChanged()
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
                        trackStarted(StartedBy.AUTO)
                        queueChanged()
                    }
                }
            },
            onPlayingChanged = {
                log.playingChanged(it)
                crossfade.onPlayingChanged(it)
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

    private val exposedPlayers = HashMap<ExoPlayer, QueueAwarePlayer>()

    /** What the session offers to controllers: a view of whichever player is playing now. */
    val exposed: QueueAwarePlayer get() = exposedPlayers.getOrPut(player) { QueueAwarePlayer(player, queue, this) }

    override val activePlayer: ExoPlayer get() = player

    init {
        player.addListener(playerEvents)
        crossfade.attach(this)
    }

    override suspend fun planNext(): PlannedTransition? =
        mutex.withLock {
            prepareWindow()
            val outgoing =
                queue.current
                    ?.ref
                    ?.songId
                    ?.let(cache::get) ?: return@withLock null
            val next = queue.peekAdvance() ?: return@withLock null
            val incoming = cache[next.ref.songId] ?: return@withLock null
            val transition =
                Transition(
                    outgoing = outgoing,
                    incoming = incoming,
                    cause = if (seeked) TransitionCause.MANUAL_SEEK else TransitionCause.END_OF_SONG,
                    shuffled = queue.shuffled,
                    repeatOne = queue.repeatMode == RepeatMode.ONE,
                )
            plannedTransition(mapper.toMediaItem(next, incoming), transition)
        }

    /**
     * The phase 6 decision, or with the DJ mix style the planner's choice (ADR 0092). Profiles are read only for the
     * DJ style, and the planning runs off the main thread, so the Crossfade style does exactly what phase 6 did.
     */
    private suspend fun plannedTransition(
        item: MediaItem,
        transition: Transition,
    ): PlannedTransition {
        val settings = crossfadeSettings()
        val phaseSix = CrossfadePolicy.decide(settings, transition)
        if (settings.style != TransitionStyle.DJ_MIX || phaseSix !is CrossfadeDecision.Fade) {
            if (settings.style == TransitionStyle.DJ_MIX) tally?.record(TransitionPlan.HardCut.technique)
            return PlannedTransition(item, phaseSix, transition.outgoing.durationMs)
        }
        val outgoingProfile = profiles.profile(transition.outgoing.id.value)
        val incomingProfile = profiles.profile(transition.incoming.id.value)
        val plan =
            withContext(Dispatchers.Default) { planner.plan(settings, transition, outgoingProfile, incomingProfile) }
        return when (plan) {
            is TransitionPlan.Overlap ->
                PlannedTransition(
                    item,
                    CrossfadeDecision.Fade(plan.overlapMs),
                    transition.outgoing.durationMs,
                    overlap = plan,
                    fallback = phaseSix,
                )
            is TransitionPlan.Fade ->
                PlannedTransition(item, CrossfadeDecision.Fade(plan.durationMs), transition.outgoing.durationMs)
            TransitionPlan.HardCut ->
                PlannedTransition(item, CrossfadeDecision.HardCut, transition.outgoing.durationMs)
        }
    }

    /**
     * The fade begins (ADR 0062). The outgoing row ends as completed, with its full duration, which is where the
     * overlap is booked; the queue moves on as it does for an automatic advance; the incoming row starts with its
     * clock held until [fadeFinished]. The session shows the incoming song from here, with its own position and length.
     */
    override suspend fun fadeStarted(incoming: ExoPlayer) =
        mutex.withLock {
            log.completed()
            queue.advanceOnTrackEnd()
            prepareWindow()
            player.removeListener(playerEvents)
            player = incoming
            incoming.addListener(playerEvents)
            sync = QueueWindowSync(queue, ExoPlayerItems(incoming, cache, mapper), config)
            queue.current?.ref?.let(sync::adopt)
            seeked = false
            log.startedDuringFade(StartedBy.AUTO)
            onPlayerChanged(exposed)
            saving.changed()
        }

    override suspend fun fadeFinished() = mutex.withLock { log.fadeEnded() }

    /** The owner dragged or tapped the seek bar, or fast-forwarded: the end of this song is a hard cut. */
    fun userSeeked() {
        seeked = true
        crossfade.invalidate()
    }

    /** New crossfade settings arrived: the plan for the next transition is made again. */
    fun crossfadeSettingsChanged() = crossfade.invalidate()

    /** Every path that ends a song and starts another goes through here, so the seek flag always starts clean. */
    private fun trackStarted(startedBy: StartedBy) {
        seeked = false
        log.started(startedBy)
    }

    /** The queue or the current song changed: save it, and plan the next transition again. */
    private fun queueChanged() {
        saving.changed()
        crossfade.invalidate()
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
                request.mixId,
                request.soloSeed,
            )
            prepareWindow()
            sync.onQueueReplaced()
            player.prepare()
            player.play()
            trackStarted(if (request.picked) StartedBy.PICKED else StartedBy.AUTO)
            exposed.notifyModes()
            queueChanged()
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
                    queueChanged()
                }
            }
        }

    fun playNext(refs: List<SongRef>) = serial { edit { queue.playNext(refs) } }

    fun addToQueue(refs: List<SongRef>) = serial { edit { queue.addToQueue(refs) } }

    /**
     * Appends a refill batch at the tail (ADR 0078). Dropped when the queue it was built for is gone (another epoch or
     * seed) or no longer wants one (repeat on, no seed); songs already queued, played ones included, are left out. The
     * edit is outside the player's window, so it announces itself (ADR 0077).
     */
    fun appendBatch(
        refs: List<SongRef>,
        seed: RefillSeed,
        epoch: Int,
        source: PlaySource,
    ) = serial {
        val signal = queue.refillSignal()
        if (signal == null || signal.epoch != epoch || signal.seed != seed) return@serial
        val queued = signal.queued.toHashSet()
        val fresh = refs.distinct().filter { it !in queued }
        if (fresh.isEmpty()) return@serial
        edit { queue.append(fresh, source, seed) }
        exposed.notifyModes()
    }

    fun jumpToUpcoming(index: Int) =
        serial {
            queue.jumpTo(queue.index + 1 + index) ?: return@serial
            prepareWindow()
            sync.fill()
            player.play()
            trackStarted(StartedBy.PICKED)
            queueChanged()
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
        trackStarted(StartedBy.PICKED)
        exposed.notifyModes()
        queueChanged()
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
                trackStarted(StartedBy.NAV)
            } else {
                player.pause()
                log.awaitFirstPlay()
            }
        }
        exposed.notifyModes()
        queueChanged()
    }

    /**
     * Drags one upcoming entry to another place (ADR 0081). Named by place and song, so a stale place changes nothing.
     * The edit goes through the serialised path like Play next, and announces itself because a move beyond the
     * player's window changes no player item (ADR 0077). Nothing is logged: no song starts or ends.
     */
    fun moveInQueue(
        from: Int,
        to: Int,
        songId: SongId,
    ) = serial {
        if (queue.entryAt(from)?.ref?.songId != songId) return@serial
        val target = queue.moveTarget(from, to) ?: return@serial
        edit { queue.move(from, target) }
        exposed.notifyModes()
    }

    /** Puts a removed entry back (undo). A removed current song becomes current again, at its position. */
    fun restoreToQueue(removed: RemovedQueueEntry) =
        serial {
            queue.insertAt(
                removed.index,
                QueueEntry(
                    removed.ref,
                    removed.source,
                    removed.manual,
                    removed.playlistId,
                    removed.mixId,
                    removed.soloSeed,
                ),
            )
            prepareWindow()
            if (removed.wasCurrent) {
                queue.jumpTo(removed.index.coerceAtMost(queue.size - 1)) ?: return@serial
                sync.fill()
                player.prepare()
                player.seekTo(removed.positionMs)
                if (removed.wasPlaying) {
                    player.play()
                    trackStarted(StartedBy.NAV)
                } else {
                    player.pause()
                    log.awaitFirstPlay()
                }
            } else {
                sync.onQueueEdited()
            }
            exposed.notifyModes()
            queueChanged()
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
            userSeeked()
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
        trackStarted(StartedBy.PICKED)
        queueChanged()
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
        crossfade.release()
        log.stopped()
        saving.saveBlocking()
        player.removeListener(playerEvents)
    }

    /** Edits the queue's order, then brings the part of the player after the current song in line. */
    private suspend fun edit(change: () -> Unit) {
        change()
        prepareWindow()
        sync.onQueueReordered()
        queueChanged()
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
        trackStarted(StartedBy.NAV)
        queueChanged()
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
