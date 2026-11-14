package dev.tomasflopes.musicplayer.data.di

import android.content.Context
import android.net.ConnectivityManager
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.tomasflopes.musicplayer.core.artistimage.ArtistCovers
import dev.tomasflopes.musicplayer.core.artistimage.ArtistImageLookupCache
import dev.tomasflopes.musicplayer.core.artistimage.ArtistImageOverrideStore
import dev.tomasflopes.musicplayer.core.artistimage.ArtistNameMatcher
import dev.tomasflopes.musicplayer.core.artistimage.ArtistPhotoSettings
import dev.tomasflopes.musicplayer.core.artistimage.DeezerClient
import dev.tomasflopes.musicplayer.core.artistimage.ModeOnlinePolicy
import dev.tomasflopes.musicplayer.core.artistimage.OnlinePolicy
import dev.tomasflopes.musicplayer.core.artistimage.TimeSource
import dev.tomasflopes.musicplayer.core.model.ports.AlbumReader
import dev.tomasflopes.musicplayer.core.model.ports.SongReader
import dev.tomasflopes.musicplayer.data.artistimage.DataStoreArtistPhotoSettings
import dev.tomasflopes.musicplayer.data.artistimage.HttpUrlDeezerClient
import dev.tomasflopes.musicplayer.data.artistimage.ReaderArtistCovers
import dev.tomasflopes.musicplayer.data.artistimage.RoomArtistImageLookupCache
import dev.tomasflopes.musicplayer.data.artistimage.RoomArtistImageOverrideStore
import dev.tomasflopes.musicplayer.data.artistimage.TextNormalizerNameMatcher
import dev.tomasflopes.musicplayer.data.artistimage.networkKind
import dev.tomasflopes.musicplayer.data.db.MusicDatabase
import java.time.Clock
import javax.inject.Singleton

/** The ports of `:core:artistimage` that need Android, Room or the network (ADR 0028, 0029, 0030). */
@Module
@InstallIn(SingletonComponent::class)
object ArtistImageDataModule {
    @Provides
    @Singleton
    fun timeSource(clock: Clock): TimeSource = TimeSource { clock.millis() }

    @Provides
    @Singleton
    fun overrideStore(
        db: MusicDatabase,
        time: TimeSource,
    ): ArtistImageOverrideStore = RoomArtistImageOverrideStore(db.artistImageDao(), time)

    @Provides
    @Singleton
    fun lookupCache(db: MusicDatabase): ArtistImageLookupCache = RoomArtistImageLookupCache(db.artistImageDao())

    @Provides
    @Singleton
    fun deezerClient(): DeezerClient = HttpUrlDeezerClient()

    @Provides
    @Singleton
    fun nameMatcher(): ArtistNameMatcher = TextNormalizerNameMatcher()

    @Provides
    @Singleton
    fun covers(
        albums: AlbumReader,
        songs: SongReader,
    ): ArtistCovers = ReaderArtistCovers(albums, songs)

    @Provides
    @Singleton
    fun photoSettings(store: DataStore<Preferences>): ArtistPhotoSettings = DataStoreArtistPhotoSettings(store)

    @Provides
    @Singleton
    fun onlinePolicy(
        @ApplicationContext context: Context,
        settings: ArtistPhotoSettings,
    ): OnlinePolicy {
        val connectivity = checkNotNull(context.getSystemService(ConnectivityManager::class.java))
        return ModeOnlinePolicy(settings) { connectivity.networkKind() }
    }
}
