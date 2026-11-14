package dev.tomasflopes.musicplayer.playback

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import dev.tomasflopes.musicplayer.core.model.SongId
import dev.tomasflopes.musicplayer.core.model.SongRef
import dev.tomasflopes.musicplayer.core.model.ports.SongReader
import dev.tomasflopes.musicplayer.core.model.queue.QueueSlot
import dev.tomasflopes.musicplayer.core.model.queue.queueLayoutOf
import dev.tomasflopes.musicplayer.playback.queue.PlayQueue
import dev.tomasflopes.musicplayer.playback.queue.RepeatMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.conflate
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
    private var ticker: Job? = null

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
        }, Runnable::run)
        scope.launch { refresh.conflate().collect { publish() } }
    }

    override fun play(request: PlayRequest) = send(PlaybackCommands.play, PlaybackCommands.bundleOf(request))

    override fun queueContent(): QueueContent = queue.content()

    override fun playNext(songs: List<SongRef>) = send(PlaybackCommands.playNext, PlaybackCommands.bundleOf(songs))

    override fun addToQueue(songs: List<SongRef>) = send(PlaybackCommands.addToQueue, PlaybackCommands.bundleOf(songs))

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
        refresh.tryEmit(Unit)
        if (player.isPlaying && ticker?.isActive != true) {
            ticker =
                scope.launch {
                    while (true) {
                        delay(config.positionTickMs)
                        refresh.tryEmit(Unit)
                    }
                }
        } else if (!player.isPlaying) {
            ticker?.cancel()
        }
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
        val loaded = songs.songsByIds(window.map { it.second.ref.songId }.distinct()).associateBy { it.id }
        val layout =
            queueLayoutOf(
                window.map { (index, entry) -> QueueSlot(index, entry.ref.songId, entry.manual) },
                currentIndex,
            ) { it in loaded }
        _state.value =
            PlaybackState(
                current = layout.current?.let { loaded[it.songId] },
                isPlaying = player?.isPlaying == true,
                positionMs = player?.currentPosition ?: 0,
                durationMs = (player?.duration ?: 0).coerceAtLeast(0),
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
            )
    }
}
