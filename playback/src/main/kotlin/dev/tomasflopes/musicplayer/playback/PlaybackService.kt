package dev.tomasflopes.musicplayer.playback

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSourceBitmapLoader
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import dagger.hilt.android.AndroidEntryPoint
import dev.tomasflopes.musicplayer.core.model.ports.PlayHistoryWriter
import dev.tomasflopes.musicplayer.core.model.ports.SongReader
import dev.tomasflopes.musicplayer.playback.log.PendingPlayStore
import dev.tomasflopes.musicplayer.playback.log.PlayEventRecorder
import dev.tomasflopes.musicplayer.playback.queue.FileQueueStore
import dev.tomasflopes.musicplayer.playback.queue.PlayQueue
import dev.tomasflopes.musicplayer.playback.queue.QueueSaver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The one place music plays: one ExoPlayer behind one session, so notification, car and headset all see it. */
@AndroidEntryPoint
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {
    @Inject lateinit var queue: PlayQueue

    @Inject lateinit var songs: SongReader

    @Inject lateinit var config: PlaybackConfig

    @Inject lateinit var mapper: MediaItemMapper

    @Inject lateinit var history: PlayHistoryWriter

    @Inject lateinit var covers: CoverBitmapSource

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    // Cover reads never touch the main thread: the session's bitmap loader launches them here.
    private val coverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var engine: PlaybackEngine? = null
    private var session: MediaSession? = null
    private var restoring: Job? = null

    override fun onCreate() {
        super.onCreate()
        val player =
            ExoPlayer
                .Builder(this)
                .setAudioAttributes(
                    AudioAttributes
                        .Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .build(),
                    true,
                ).setHandleAudioBecomingNoisy(true)
                .setWakeMode(C.WAKE_MODE_LOCAL)
                .build()
        val recorder = PlayEventRecorder(history, scope, PendingPlayStore(filesDir.resolve("pending-play")))
        val store = FileQueueStore(filesDir.resolve("queue").resolve("queue.bin"))
        // One write at a time, off the main thread; the queue itself is only read on the main thread (ADR 0036).
        val queueIo = Dispatchers.IO.limitedParallelism(1)
        val saver = QueueSaver(queue, store, player::getCurrentPosition, scope, queueIo, config.queueSaveDebounceMs)
        val engine = PlaybackEngine(player, queue, songs, mapper, config, scope, recorder, store, saver)
        this.engine = engine
        session =
            MediaSession
                .Builder(this, engine.exposed)
                .setSessionActivity(openAppIntent())
                .setBitmapLoader(coverLoader())
                .setCallback(CommandCallback(engine, scope) { restoring })
                .build()
        restoring = engine.restore()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (session?.player?.playWhenReady != true) pauseAllPlayersAndStopSelf()
    }

    override fun onDestroy() {
        // The engine first: it saves the queue with the position of a player that is still alive.
        engine?.release()
        session?.run {
            player.release()
            release()
        }
        scope.cancel()
        coverScope.cancel()
        super.onDestroy()
    }

    private fun coverLoader() =
        CoverBitmapLoader(
            delegate = DataSourceBitmapLoader.Builder(this).build(),
            songs = songs,
            covers = covers,
            sizePx = config.notificationCoverPx,
            scope = coverScope,
        )

    private fun openAppIntent(): PendingIntent =
        PendingIntent.getActivity(
            this,
            0,
            packageManager.getLaunchIntentForPackage(packageName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private class CommandCallback(
        private val engine: PlaybackEngine,
        private val scope: CoroutineScope,
        private val restoring: () -> Job?,
    ) : MediaSession.Callback {
        /**
         * A headset, car or notification asked a session whose process had died to play (ADR 0036): wait for the
         * restore, then hand Media3 the restored window so it carries on from there.
         */
        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            isForPlayback: Boolean,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val future = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
            scope.launch {
                restoring()?.join()
                val player = mediaSession.player
                if (player.mediaItemCount == 0) {
                    future.setException(UnsupportedOperationException("No saved queue to resume"))
                } else {
                    val items = (0 until player.mediaItemCount).map(player::getMediaItemAt)
                    future.set(
                        MediaSession.MediaItemsWithStartPosition(
                            items,
                            player.currentMediaItemIndex,
                            player.currentPosition,
                        ),
                    )
                }
            }
            return future
        }

        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
            PlaybackCommands.all.forEach { commands.add(it) }
            return MediaSession.ConnectionResult.accept(
                commands.build(),
                MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS,
            )
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            when (customCommand) {
                PlaybackCommands.play -> engine.play(PlaybackCommands.requestOf(args))
                PlaybackCommands.playNext -> engine.playNext(PlaybackCommands.refsOf(args))
                PlaybackCommands.addToQueue -> engine.addToQueue(PlaybackCommands.refsOf(args))
                PlaybackCommands.jumpToUpcoming -> engine.jumpToUpcoming(PlaybackCommands.indexOf(args))
                else -> return Futures.immediateFuture(
                    SessionResult(SessionError(SessionError.ERROR_NOT_SUPPORTED, "Unknown command")),
                )
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
    }
}
