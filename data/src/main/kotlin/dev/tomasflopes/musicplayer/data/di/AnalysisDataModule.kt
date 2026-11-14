package dev.tomasflopes.musicplayer.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.tomasflopes.musicplayer.core.analysis.PcmSource
import dev.tomasflopes.musicplayer.data.analysis.MediaCodecPcmSource

/** The phone's decoder behind the `PcmSource` port of `:core:analysis` (ADR 0053). */
@Module
@InstallIn(SingletonComponent::class)
abstract class AnalysisDataModule {
    @Binds
    abstract fun pcmSource(impl: MediaCodecPcmSource): PcmSource
}
