package dev.tomasflopes.musicplayer.playback

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import dev.tomasflopes.musicplayer.core.model.Song
import dev.tomasflopes.musicplayer.core.model.SongId
import dev.tomasflopes.musicplayer.core.model.SongRef
import dev.tomasflopes.musicplayer.core.model.log.PlaySource
import dev.tomasflopes.musicplayer.core.model.ports.SongReader
import dev.tomasflopes.musicplayer.core.model.queue.QueueSlot
import dev.tomasflopes.musicplayer.core.model.queue.RefillSeed
import dev.tomasflopes.musicplayer.core.model.queue.queueLayoutOf
import dev.tomasflopes.musicplayer.playback.queue.PlayQueue
import dev.tomasflopes.musicplayer.playback.queue.RepeatMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch

/**
 * The app's side of playback: a MediaController to the service, the same kind a car or headset uses. Queue
 * contents are read from the [PlayQueue] (service and app share a process) and turned into [PlaybackState].
 */
internal class MediaControllerPlaybackController(
    context: Context,
    private val queue: PlayQueue,
    private val songs: SongReader,
    private val config: PlaybackConfig,
    private val scope: CoroutineScope,
) : PlaybackController,
    Player.Listener {
    private val _state = MutableStateFlow(PlaybackState())
    override val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private val refresh = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private var controller: MediaController? = null
    private val waiting = mutableListOf<(MediaController) -> Unit>()

    // What the last publish was built from, so an event that changes nothing in the window reads
    // no database (ADR 0077).
    private var publishedIds: List<SongId>? = null
    private var publishedSongs: Map<SongId, Song> = emptyMap()

    // Wakes every position flow: any player event (a seek, a pause, a new song) and the controller connecting.
    private val positionNudges =
        MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    @OptIn(ExperimentalCoroutinesApi::class)
    override val position: Flow<PlaybackPosition> =
        positionNudges
            .onStart { emit(Unit) }
            .transformLatest {
                // Reads the player only; no database and no PlaybackState. Stops with the collector, and stops
                // looping while paused until the next player event nudges it.
                while (true) {
                    emit(readPosition())
                    if (controller?.isPlaying != true) break
                    delay(config.positionTickMs)
                }
            }.distinctUntilChanged()

    init {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            val connected = future.get()
            connected.addListener(this)
            controller = connected
            waiting.forEach { it(connected) }
            waiting.clear()
            refresh.tryEmit(Unit)
            positionNudges.tryEmit(Unit)
        }, Runnable::run)
        scope.launch { refresh.conflate().collect { publish() } }
    }

    override fun play(request: PlayRequest) = send(PlaybackCommands.play, PlaybackCommands.bundleOf(request))

    override fun queueContent(): QueueContent = queue.content()

    override fun playNext(songs: List<SongRef>) = send(PlaybackCommands.playNext, PlaybackCommands.bundleOf(songs))

    override fun addToQueue(songs: List<SongRef>) = send(PlaybackCommands.addToQueue, PlaybackCommands.bundleOf(songs))

    override fun appendBatch(
        songs: List<SongRef>,
        seed: RefillSeed,
        epoch: Int,
        source: PlaySource,
    ) = send(PlaybackCommands.appendBatch, PlaybackCommands.bundleOf(songs, seed, epoch, source))

    override fun jumpToUpcoming(index: Int) = send(PlaybackCommands.jumpToUpcoming, PlaybackCommands.bundleOf(index))

    override fun jumpToQueueIndex(
        index: Int,
        songId: SongId,
    ) = send(PlaybackCommands.jumpToQueueIndex, PlaybackCommands.bundleOf(index, songId))

    override fun removeFromQueue(
        index: Int,
        songId: SongId,
    ): RemovedQueueEntry? {
        val entry = queue.entryAt(index)?.takeIf { it.ref.songId == songId } ?: return null
        val removed =
            RemovedQueueEntry(
                index = index,
                ref = entry.ref,
                source = entry.source,
                manual = entry.manual,
                playlistId = entry.playlistId,
                mixId = entry.mixId,
                soloSeed = entry.soloSeed,
                wasCurrent = index == queue.index,
                positionMs = controller?.currentPosition ?: 0,
                wasPlaying = controller?.playWhenReady == true,
            )
        send(PlaybackCommands.removeFromQueue, PlaybackCommands.bundleOf(index, songId))
        return removed
    }

    override fun restoreToQueue(entry: RemovedQueueEntry) =
        send(PlaybackCommands.restoreToQueue, PlaybackCommands.bundleOf(entry))

    override fun togglePlayPause() = withController { if (it.isPlaying) it.pause() else it.play() }

    override fun next() = withController { it.seekToNext() }

    override fun previous() = withController { it.seekToPrevious() }

    override fun seekTo(positionMs: Long) = withController { it.seekTo(positionMs) }

    override fun setShuffle(on: Boolean) = withController { it.shuffleModeEnabled = on }

    override fun cycleRepeatMode() =
        withController {
            it.repeatMode =
                when (queue.repeatMode) {
                    RepeatMode.OFF -> Player.REPEAT_MODE_ALL
                    RepeatMode.ALL -> Player.REPEAT_MODE_ONE
                    RepeatMode.ONE -> Player.REPEAT_MODE_OFF
                }
        }

    override fun onEvents(
        player: Player,
        events: Player.Events,
    ) {
        positionNudges.tryEmit(Unit)
        if (events.containsAny(*QUEUE_EVENTS)) refresh.tryEmit(Unit)
    }

    private fun send(
        command: androidx.media3.session.SessionCommand,
        args: android.os.Bundle,
    ) = withController { it.sendCustomCommand(command, args) }

    private fun withController(action: (MediaController) -> Unit) {
        controller?.let(action) ?: waiting.add(action)
    }

    private suspend fun publish() {
        val player = controller
        val currentIndex = queue.index
        val window = queue.indexedWindow(behind = config.historyShown, ahead = config.windowAhead)
        val ids = window.map { it.second.ref.songId }.distinct()
        val loaded =
            if (ids == publishedIds) {
                publishedSongs
            } else {
                songs.songsByIds(ids).associateBy { it.id }.also {
                    publishedIds = ids
                    publishedSongs = it
                }
            }
        val layout =
            queueLayoutOf(
                window.map { (index, entry) -> QueueSlot(index, entry.ref.songId, entry.manual) },
                currentIndex,
            ) { it in loaded }
        _state.value =
            PlaybackState(
                current = layout.current?.let { loaded[it.songId] },
                isPlaying = player?.isPlaying == true,
                upcoming = layout.upcoming.mapNotNull { loaded[it.songId] },
                manualUpcoming = layout.manualUpcoming,
                played = layout.played.mapNotNull { loaded[it.songId] },
                playedIndices = layout.played.map { it.index },
                currentIndex = layout.current?.index ?: currentIndex,
                upcomingIndices = layout.upcoming.map { it.index },
                hasPrevious = queue.index > 0 || (queue.repeatMode != RepeatMode.OFF && queue.size > 0),
                hasNext = queue.index < queue.size - 1 || (queue.repeatMode != RepeatMode.OFF && queue.size > 0),
                shuffle = queue.shuffled,
                repeatMode = queue.repeatMode,
                refill = queue.refillSignal(),
            )
    }

    private fun readPosition(): PlaybackPosition {
        val player = controller ?: return PlaybackPosition()
        return PlaybackPosition(player.currentPosition.coerceAtLeast(0), player.duration.coerceAtLeast(0))
    }

    private companion object {
        /** The player events that change [PlaybackState]; the custom commands announce themselves as mode events. */
        val QUEUE_EVENTS =
            intArrayOf(
                Player.EVENT_MEDIA_ITEM_TRANSITION,
                Player.EVENT_TIMELINE_CHANGED,
                Player.EVENT_IS_PLAYING_CHANGED,
                Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
                Player.EVENT_REPEAT_MODE_CHANGED,
                Player.EVENT_PLAYBACK_STATE_CHANGED,
            )
    }
}
