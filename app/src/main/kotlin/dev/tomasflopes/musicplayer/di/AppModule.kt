package dev.tomasflopes.musicplayer.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.tomasflopes.musicplayer.core.model.config.LibraryConfig
import dev.tomasflopes.musicplayer.ui.art.CoverArtLoader
import dev.tomasflopes.musicplayer.ui.art.MediaStoreCoverArtLoader
import java.time.Clock
import javax.inject.Singleton

/** Where tunables and app-level services that several modules read are created, once. */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun libraryConfig() = LibraryConfig()

    @Provides
    @Singleton
    fun clock(): Clock = Clock.systemUTC()

    @Provides
    @Singleton
    fun coverArtLoader(
        @ApplicationContext context: Context,
    ): CoverArtLoader = MediaStoreCoverArtLoader(context.contentResolver)
}
