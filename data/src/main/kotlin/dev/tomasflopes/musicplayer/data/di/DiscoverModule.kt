package dev.tomasflopes.musicplayer.data.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.tomasflopes.musicplayer.core.model.ports.PlayedSongSource
import dev.tomasflopes.musicplayer.core.model.ports.SongReader
import dev.tomasflopes.musicplayer.core.model.ports.SoundVectorSource
import dev.tomasflopes.musicplayer.core.taste.config.DiscoverConfig
import dev.tomasflopes.musicplayer.core.taste.lists.DiscoverPolicy
import dev.tomasflopes.musicplayer.core.taste.lists.DiscoverProvider
import dev.tomasflopes.musicplayer.core.taste.lists.SoundAwareDiscoverPolicy
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
     * start time, so the list differs from one app start to the next (ADR 0063). The rule is the sound-aware one
     * (ADR 0071); `DiscoverPolicy` stays as the artist-only alternative and as its fallback.
     */
    @Provides @Singleton
    fun discoverProvider(
        songReader: SongReader,
        played: PlayedSongSource,
        board: ScoreBoard,
        vectors: SoundVectorSource,
        clock: Clock,
    ): DiscoverProvider {
        val config = DiscoverConfig()
        return DiscoverProvider(
            songReader,
            played,
            board,
            SoundAwareDiscoverPolicy(config, fallback = DiscoverPolicy(config)),
            clock.millis(),
            vectors,
        )
    }
}
