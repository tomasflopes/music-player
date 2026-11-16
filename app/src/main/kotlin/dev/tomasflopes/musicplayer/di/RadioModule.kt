package dev.tomasflopes.musicplayer.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.tomasflopes.musicplayer.core.model.ports.PlayedSinceSource
import dev.tomasflopes.musicplayer.core.model.ports.SongReader
import dev.tomasflopes.musicplayer.core.model.ports.SoundVectorSource
import dev.tomasflopes.musicplayer.core.taste.config.RadioConfig
import dev.tomasflopes.musicplayer.core.taste.lists.RadioPolicy
import dev.tomasflopes.musicplayer.playback.PlaybackController
import dev.tomasflopes.musicplayer.radio.ShortMessages
import dev.tomasflopes.musicplayer.radio.StartRadio
import dev.tomasflopes.musicplayer.radio.ToastMessages
import java.time.Clock

/** Radio v0 (ADR 0072): the use case is a plain class; the annotations live here (plan D5). */
@Module
@InstallIn(SingletonComponent::class)
abstract class RadioBindingsModule {
    @Binds
    abstract fun shortMessages(impl: ToastMessages): ShortMessages
}

@Module
@InstallIn(SingletonComponent::class)
object RadioModule {
    @Provides fun radioConfig() = RadioConfig()

    @Provides fun startRadio(
        songs: SongReader,
        vectors: SoundVectorSource,
        played: PlayedSinceSource,
        playback: PlaybackController,
        messages: ShortMessages,
        config: RadioConfig,
        clock: Clock,
    ) = StartRadio(songs, vectors, played, playback, messages, RadioPolicy(config), config, clock)
}
