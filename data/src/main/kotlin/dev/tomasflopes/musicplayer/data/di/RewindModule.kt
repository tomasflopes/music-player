package dev.tomasflopes.musicplayer.data.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.tomasflopes.musicplayer.core.model.ports.PlayEventSource
import dev.tomasflopes.musicplayer.core.model.ports.PlayedSinceSource
import dev.tomasflopes.musicplayer.core.model.ports.PlaylistAddSource
import dev.tomasflopes.musicplayer.core.model.ports.SearchEventSource
import dev.tomasflopes.musicplayer.core.model.ports.SongReader
import dev.tomasflopes.musicplayer.core.taste.config.RewindConfig
import dev.tomasflopes.musicplayer.core.taste.config.TasteConfig
import dev.tomasflopes.musicplayer.core.taste.lists.RewindLogs
import dev.tomasflopes.musicplayer.core.taste.lists.RewindPolicy
import dev.tomasflopes.musicplayer.core.taste.lists.RewindProvider
import dev.tomasflopes.musicplayer.core.taste.score.PastScorer
import dev.tomasflopes.musicplayer.data.repo.RoomPlayHistory
import java.time.Clock
import javax.inject.Singleton

/** What the Rewind list needs, apart from [RepositoryModule] to keep that one under the function limit. */
@Module
@InstallIn(SingletonComponent::class)
object RewindModule {
    @Provides fun playedSinceSource(history: RoomPlayHistory): PlayedSinceSource = history

    /**
     * One instance for the process, so the Home card and the list screen show the same songs: the seed is the
     * start time, so the list differs from one app start to the next (ADR 0064).
     */
    @Provides @Singleton
    fun rewindProvider(
        songReader: SongReader,
        played: PlayedSinceSource,
        events: PlayEventSource,
        playlistAdds: PlaylistAddSource,
        searches: SearchEventSource,
        tasteConfig: TasteConfig,
        clock: Clock,
    ): RewindProvider =
        RewindProvider(
            songReader,
            RewindLogs(played, events, playlistAdds, searches),
            RewindPolicy(RewindConfig(), PastScorer(tasteConfig)),
            clock,
            clock.millis(),
        )
}
