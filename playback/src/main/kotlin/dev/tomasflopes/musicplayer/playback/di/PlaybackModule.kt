package dev.tomasflopes.musicplayer.playback.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.tomasflopes.musicplayer.core.model.ports.SongReader
import dev.tomasflopes.musicplayer.core.taste.shuffle.PlainShuffle
import dev.tomasflopes.musicplayer.core.taste.shuffle.ShuffleStrategy
import dev.tomasflopes.musicplayer.playback.MediaControllerPlaybackController
import dev.tomasflopes.musicplayer.playback.MediaItemMapper
import dev.tomasflopes.musicplayer.playback.PlaybackConfig
import dev.tomasflopes.musicplayer.playback.PlaybackController
import dev.tomasflopes.musicplayer.playback.queue.PlayQueue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton
import kotlin.random.Random

@Module
@InstallIn(SingletonComponent::class)
object PlaybackModule {
    @Provides @Singleton
    fun config() = PlaybackConfig()

    @Provides
    fun shuffleStrategy(): ShuffleStrategy = PlainShuffle()

    /** One queue for the whole process: the service plays it, the controller reads it. */
    @Provides @Singleton
    fun queue(strategy: ShuffleStrategy) = PlayQueue(strategy, Random.Default)

    @Provides
    fun mapper() = MediaItemMapper()

    @Provides @Singleton
    fun controller(
        @ApplicationContext context: Context,
        queue: PlayQueue,
        songs: SongReader,
        config: PlaybackConfig,
    ): PlaybackController =
        MediaControllerPlaybackController(
            context,
            queue,
            songs,
            config,
            CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
        )
}
