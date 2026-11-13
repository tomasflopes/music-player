package dev.tomasflopes.musicplayer.data.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.tomasflopes.musicplayer.core.model.ports.AlbumReader
import dev.tomasflopes.musicplayer.core.model.ports.ArtistReader
import dev.tomasflopes.musicplayer.core.model.ports.FolderReader
import dev.tomasflopes.musicplayer.core.model.ports.PlayHistoryReader
import dev.tomasflopes.musicplayer.core.model.ports.PlayHistoryWriter
import dev.tomasflopes.musicplayer.core.model.ports.SearchHistoryWriter
import dev.tomasflopes.musicplayer.core.model.ports.SettingsRepository
import dev.tomasflopes.musicplayer.core.model.ports.SongFeatureReader
import dev.tomasflopes.musicplayer.core.model.ports.SongReader
import dev.tomasflopes.musicplayer.core.taste.lists.RecentlyAddedProvider
import dev.tomasflopes.musicplayer.data.db.MusicDatabase
import dev.tomasflopes.musicplayer.data.repo.RoomAlbumReader
import dev.tomasflopes.musicplayer.data.repo.RoomArtistReader
import dev.tomasflopes.musicplayer.data.repo.RoomFeatureReader
import dev.tomasflopes.musicplayer.data.repo.RoomFolderReader
import dev.tomasflopes.musicplayer.data.repo.RoomPlayHistory
import dev.tomasflopes.musicplayer.data.repo.RoomSearchHistory
import dev.tomasflopes.musicplayer.data.repo.RoomSongReader
import java.time.Clock
import javax.inject.Singleton

/** The `:core:model` ports, implemented over Room. */
@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {
    @Provides fun songReader(db: MusicDatabase): SongReader = RoomSongReader(db)

    @Provides fun songFeatureReader(db: MusicDatabase): SongFeatureReader = RoomFeatureReader(db)

    @Provides fun recentlyAddedProvider(
        songReader: SongReader,
        settings: SettingsRepository,
        clock: Clock,
    ): RecentlyAddedProvider = RecentlyAddedProvider(songReader, settings, clock)

    @Provides fun artistReader(db: MusicDatabase): ArtistReader = RoomArtistReader(db)

    @Provides fun albumReader(db: MusicDatabase): AlbumReader = RoomAlbumReader(db)

    @Provides fun folderReader(db: MusicDatabase): FolderReader = RoomFolderReader(db)

    @Provides @Singleton
    fun playHistory(db: MusicDatabase): RoomPlayHistory = RoomPlayHistory(db)

    @Provides fun playHistoryWriter(history: RoomPlayHistory): PlayHistoryWriter = history

    @Provides fun playHistoryReader(history: RoomPlayHistory): PlayHistoryReader = history

    @Provides fun searchHistoryWriter(db: MusicDatabase): SearchHistoryWriter = RoomSearchHistory(db)
}
