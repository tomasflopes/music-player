package dev.tomasflopes.musicplayer.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.tomasflopes.musicplayer.core.model.mixes.CreateMix
import dev.tomasflopes.musicplayer.core.model.mixes.DeleteMix
import dev.tomasflopes.musicplayer.core.model.mixes.MixReader
import dev.tomasflopes.musicplayer.core.model.mixes.MixStore
import dev.tomasflopes.musicplayer.core.model.mixes.UpdateMix
import dev.tomasflopes.musicplayer.core.model.ports.PlayEventSource
import dev.tomasflopes.musicplayer.core.model.ports.PlayedSinceSource
import dev.tomasflopes.musicplayer.core.model.ports.SongReader
import dev.tomasflopes.musicplayer.core.model.ports.SoundVectorSource
import dev.tomasflopes.musicplayer.core.taste.config.MixConfig
import dev.tomasflopes.musicplayer.core.taste.config.TasteConfig
import dev.tomasflopes.musicplayer.core.taste.mix.AnchorQuery
import dev.tomasflopes.musicplayer.core.taste.mix.MixEngine
import dev.tomasflopes.musicplayer.core.taste.mix.MixScoreBoard
import dev.tomasflopes.musicplayer.core.taste.mix.OnDemandMixScoreBoard
import dev.tomasflopes.musicplayer.mix.StartMix
import dev.tomasflopes.musicplayer.playback.PlaybackController
import dev.tomasflopes.musicplayer.radio.ShortMessages
import java.time.Clock

/** Mixes (ADR 0076): the use cases are plain classes; the annotations live here (plan D5). Anchor query by default. */
@Module
@InstallIn(SingletonComponent::class)
object MixModule {
    @Provides fun mixConfig() = MixConfig()

    @Provides fun mixEngine(config: MixConfig) = MixEngine(config, AnchorQuery(config))

    /** What a mix learned from its own rows, scored on demand (ADR 0085). */
    @Provides fun mixScoreBoard(
        events: PlayEventSource,
        taste: TasteConfig,
        config: MixConfig,
    ): MixScoreBoard = OnDemandMixScoreBoard(events, taste, config)

    @Provides fun createMix(
        store: MixStore,
        clock: Clock,
    ) = CreateMix(store, clock)

    @Provides fun updateMix(
        store: MixStore,
        clock: Clock,
    ) = UpdateMix(store, clock)

    @Provides fun deleteMix(store: MixStore) = DeleteMix(store)

    @Provides fun startMix(
        mixes: MixReader,
        songs: SongReader,
        vectors: SoundVectorSource,
        played: PlayedSinceSource,
        scores: MixScoreBoard,
        playback: PlaybackController,
        messages: ShortMessages,
        engine: MixEngine,
        config: MixConfig,
        clock: Clock,
    ) = StartMix(mixes, songs, vectors, played, scores, playback, messages, engine, config, clock)
}
