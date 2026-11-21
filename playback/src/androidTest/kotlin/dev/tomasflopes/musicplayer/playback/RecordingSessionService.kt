package dev.tomasflopes.musicplayer.playback

import android.os.Bundle
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.LinkedBlockingQueue

/**
 * A session service for tests: accepts every [PlaybackCommands] command like [PlaybackService] and records the custom
 * actions it receives, so a test can see what actually crossed the Media3 boundary.
 */
class RecordingSessionService : MediaSessionService() {
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        session = MediaSession.Builder(this, ExoPlayer.Builder(this).build()).setCallback(Callback).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = session

    override fun onDestroy() {
        session?.run {
            player.release()
            release()
        }
        super.onDestroy()
    }

    private object Callback : MediaSession.Callback {
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
            received.add(customCommand.customAction)
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
    }

    companion object {
        /** The custom actions received, in arrival order. */
        val received = LinkedBlockingQueue<String>()
    }
}
