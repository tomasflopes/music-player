package dev.tomasflopes.musicplayer.data.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.tomasflopes.musicplayer.core.model.ports.AlbumReader
import dev.tomasflopes.musicplayer.core.model.ports.ArtistReader
import dev.tomasflopes.musicplayer.core.model.ports.FolderReader
import dev.tomasflopes.musicplayer.core.model.ports.PlayEventSource
import dev.tomasflopes.musicplayer.core.model.ports.PlayHistoryReader
import dev.tomasflopes.musicplayer.core.model.ports.PlayHistoryWriter
import dev.tomasflopes.musicplayer.core.model.ports.SearchHistoryWriter
import dev.tomasflopes.musicplayer.core.model.ports.SettingsRepository
import dev.tomasflopes.musicplayer.core.model.ports.SongFeatureReader
import dev.tomasflopes.musicplayer.core.model.ports.SongReader
import dev.tomasflopes.musicplayer.core.taste.config.TasteConfig
import dev.tomasflopes.musicplayer.core.taste.lists.RecentlyAddedProvider
import dev.tomasflopes.musicplayer.core.taste.lists.TopHundredProvider
import dev.tomasflopes.musicplayer.core.taste.score.OnDemandScoreBoard
import dev.tomasflopes.musicplayer.core.taste.score.ScoreBoard
import dev.tomasflopes.musicplayer.core.taste.score.TasteScorer
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

    /** The decision point of ADR 0023: bind a cached `ScoreBoard` here to replace the on-demand one. */
    @Provides fun scoreBoard(
        events: PlayEventSource,
        config: TasteConfig,
        clock: Clock,
    ): ScoreBoard = OnDemandScoreBoard(events, TasteScorer(config), config, clock)

    @Provides fun topHundredProvider(
        board: ScoreBoard,
        songReader: SongReader,
        config: TasteConfig,
    ): TopHundredProvider = TopHundredProvider(board, songReader, config)

    @Provides fun artistReader(db: MusicDatabase): ArtistReader = RoomArtistReader(db)

    @Provides fun albumReader(db: MusicDatabase): AlbumReader = RoomAlbumReader(db)

    @Provides fun folderReader(db: MusicDatabase): FolderReader = RoomFolderReader(db)

    @Provides @Singleton
    fun playHistory(db: MusicDatabase): RoomPlayHistory = RoomPlayHistory(db)

    @Provides fun playHistoryWriter(history: RoomPlayHistory): PlayHistoryWriter = history

    @Provides fun playHistoryReader(history: RoomPlayHistory): PlayHistoryReader = history

    @Provides fun playEventSource(history: RoomPlayHistory): PlayEventSource = history

    @Provides fun searchHistoryWriter(db: MusicDatabase): SearchHistoryWriter = RoomSearchHistory(db)
}
