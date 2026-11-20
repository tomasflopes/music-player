package dev.tomasflopes.musicplayer.di

import dagger.Lazy
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import dev.tomasflopes.musicplayer.core.model.mixes.MixReader
import dev.tomasflopes.musicplayer.core.model.ports.PlayedSinceSource
import dev.tomasflopes.musicplayer.core.model.ports.SettingsRepository
import dev.tomasflopes.musicplayer.core.model.ports.SongReader
import dev.tomasflopes.musicplayer.core.model.ports.SoundVectorSource
import dev.tomasflopes.musicplayer.core.taste.config.MixConfig
import dev.tomasflopes.musicplayer.core.taste.config.RadioConfig
import dev.tomasflopes.musicplayer.core.taste.config.SoloExtensionConfig
import dev.tomasflopes.musicplayer.core.taste.lists.RadioPolicy
import dev.tomasflopes.musicplayer.core.taste.mix.MixEngine
import dev.tomasflopes.musicplayer.playback.PlaybackController
import dev.tomasflopes.musicplayer.refill.MixRefillSource
import dev.tomasflopes.musicplayer.refill.QueueRefiller
import dev.tomasflopes.musicplayer.refill.RefillLibrary
import dev.tomasflopes.musicplayer.refill.RefillSource
import dev.tomasflopes.musicplayer.refill.SoloRefillSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.map
import java.time.Clock
import javax.inject.Singleton

/**
 * Refill (ADR 0078, 0080): one source per kind of seed in a Hilt set, so a new kind is one more `@IntoSet` line. The
 * refiller runs on its own Default scope for the life of the process and reads its sources lazily.
 */
@Module
@InstallIn(SingletonComponent::class)
object RefillModule {
    @Provides fun soloExtensionConfig() = SoloExtensionConfig()

    @Provides fun refillLibrary(
        songs: SongReader,
        played: PlayedSinceSource,
        clock: Clock,
    ) = RefillLibrary(songs, played, clock)

    @Provides @IntoSet
    fun mixRefillSource(
        mixes: MixReader,
        vectors: SoundVectorSource,
        library: RefillLibrary,
        engine: MixEngine,
        config: MixConfig,
    ): RefillSource = MixRefillSource(mixes, vectors, library, engine, config)

    @Provides @IntoSet
    fun soloRefillSource(
        settings: SettingsRepository,
        vectors: SoundVectorSource,
        library: RefillLibrary,
        solo: SoloExtensionConfig,
        radio: RadioConfig,
    ): RefillSource = SoloRefillSource(settings, vectors, library, RadioPolicy(solo.radioConfig(radio)), solo, radio)

    @Provides @Singleton
    fun queueRefiller(
        playback: PlaybackController,
        sources: Lazy<Set<@JvmSuppressWildcards RefillSource>>,
    ) = QueueRefiller(
        signals = playback.state.map { it.refill },
        sources = { sources.get() },
        playback = playback,
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    )
}
