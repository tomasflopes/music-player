package dev.tomasflopes.musicplayer.playback.di

import android.content.Context
import androidx.media3.common.util.UnstableApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import dev.tomasflopes.musicplayer.core.model.ports.SongReader
import dev.tomasflopes.musicplayer.core.model.transition.BeatMatchTechnique
import dev.tomasflopes.musicplayer.core.model.transition.CalibratedConfidenceReliability
import dev.tomasflopes.musicplayer.core.model.transition.CrossfadeTechnique
import dev.tomasflopes.musicplayer.core.model.transition.TempoReliability
import dev.tomasflopes.musicplayer.core.model.transition.TransitionConfig
import dev.tomasflopes.musicplayer.core.model.transition.TransitionPlanner
import dev.tomasflopes.musicplayer.core.model.transition.TransitionTechnique
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
import dev.tomasflopes.musicplayer.playback.bass.BassPlayers
import dev.tomasflopes.musicplayer.playback.crossfade.CrossfadeEngineFactory
import dev.tomasflopes.musicplayer.playback.crossfade.TwoPlayerCrossfadeEngine
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

    /**
     * The decision point for how songs are joined (ADR 0061): the two-player blend, or `SinglePlayerEngine.factory`
     * for the plain Media3 path. Change this one line to compare them.
     */
    @OptIn(UnstableApi::class)
    @Provides
    fun crossfadeEngineFactory(bassPlayers: BassPlayers): CrossfadeEngineFactory =
        TwoPlayerCrossfadeEngine.factory(bassPlayers)

    /** Builds the players with the bass filter (ADR 0101, 0102); the service and the engine share it. */
    @OptIn(UnstableApi::class)
    @Provides @Singleton
    fun bassPlayers(config: TransitionConfig) = BassPlayers(config)

    /** Every number of the beat-matched transitions (phase 7); none of them is a setting. */
    @Provides @Singleton
    fun transitionConfig() = TransitionConfig()

    /**
     * Which stored tempos a beat match trusts (ADR 0098, 0099). Change this one line to compare with
     * `StoredConfidenceReliability`, the slice 1 and 2 rule.
     */
    @Provides
    fun tempoReliability(): TempoReliability = CalibratedConfidenceReliability()

    /**
     * The transition techniques (ADR 0092): one `@IntoSet` line per technique, asked in priority order. The phase 6
     * crossfade is the last one and always answers. A new technique is a new class and one line here.
     */
    @Provides @IntoSet
    fun beatMatchTechnique(reliability: TempoReliability): TransitionTechnique = BeatMatchTechnique(reliability)

    @Provides @IntoSet
    fun crossfadeTechnique(): TransitionTechnique = CrossfadeTechnique()

    @Provides
    fun transitionPlanner(
        techniques: Set<@JvmSuppressWildcards TransitionTechnique>,
        config: TransitionConfig,
    ) = TransitionPlanner(techniques, config)

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
