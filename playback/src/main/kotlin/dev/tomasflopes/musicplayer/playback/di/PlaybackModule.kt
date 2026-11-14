package dev.tomasflopes.musicplayer.playback.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.tomasflopes.musicplayer.core.model.ports.SongReader
import dev.tomasflopes.musicplayer.core.taste.config.SmartShuffleConfig
import dev.tomasflopes.musicplayer.core.taste.score.ScoreBoard
import dev.tomasflopes.musicplayer.core.taste.shuffle.BoardGlobalScores
import dev.tomasflopes.musicplayer.core.taste.shuffle.GlobalScores
import dev.tomasflopes.musicplayer.core.taste.shuffle.PlainShuffle
import dev.tomasflopes.musicplayer.core.taste.shuffle.ShuffleStrategy
import dev.tomasflopes.musicplayer.core.taste.shuffle.SkipAwareShuffle
import dev.tomasflopes.musicplayer.core.taste.shuffle.SmartShuffle
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
    fun shuffleStrategy(): ShuffleStrategy = SmartShuffle(SmartShuffleConfig())

    /** What the Recently added shuffle reads: the latest global scores, followed for the life of the process. */
    @Provides @Singleton
    fun globalScores(board: ScoreBoard): GlobalScores =
        BoardGlobalScores(board, CoroutineScope(SupervisorJob() + Dispatchers.Default))

    /** One queue for the whole process: the service plays it, the controller reads it. */
    @Provides @Singleton
    fun queue(
        strategy: ShuffleStrategy,
        scores: GlobalScores,
    ) = PlayQueue(
        strategy,
        Random.Default,
        artistShuffle = PlainShuffle(),
        recentlyAdded = SkipAwareShuffle(scores, base = strategy),
    )

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
