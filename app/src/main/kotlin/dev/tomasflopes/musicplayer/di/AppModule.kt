package dev.tomasflopes.musicplayer.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.tomasflopes.musicplayer.core.model.config.LibraryConfig
import java.time.Clock
import javax.inject.Singleton

/** Where tunables that several modules read are created, once. */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun libraryConfig() = LibraryConfig()

    @Provides
    @Singleton
    fun clock(): Clock = Clock.systemUTC()
}
