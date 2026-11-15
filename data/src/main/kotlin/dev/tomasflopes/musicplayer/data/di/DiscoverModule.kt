package dev.tomasflopes.musicplayer.data.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.tomasflopes.musicplayer.core.model.ports.PlayedSongSource
import dev.tomasflopes.musicplayer.core.model.ports.SongReader
import dev.tomasflopes.musicplayer.core.taste.config.DiscoverConfig
import dev.tomasflopes.musicplayer.core.taste.lists.DiscoverPolicy
import dev.tomasflopes.musicplayer.core.taste.lists.DiscoverProvider
import dev.tomasflopes.musicplayer.core.taste.score.ScoreBoard
import dev.tomasflopes.musicplayer.data.repo.RoomPlayHistory
import java.time.Clock
import javax.inject.Singleton

/** What the Discover list needs, apart from [RepositoryModule] to keep that one under the function limit. */
@Module
@InstallIn(SingletonComponent::class)
object DiscoverModule {
    @Provides fun playedSongSource(history: RoomPlayHistory): PlayedSongSource = history

    /**
     * One instance for the process, so the Home card and the list screen show the same songs: the seed is the
     * start time, so the list differs from one app start to the next (ADR 0063).
     */
    @Provides @Singleton
    fun discoverProvider(
        songReader: SongReader,
        played: PlayedSongSource,
        board: ScoreBoard,
        clock: Clock,
    ): DiscoverProvider = DiscoverProvider(songReader, played, board, DiscoverPolicy(DiscoverConfig()), clock.millis())
}
