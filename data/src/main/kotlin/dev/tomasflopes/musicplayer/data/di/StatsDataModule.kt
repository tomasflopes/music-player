package dev.tomasflopes.musicplayer.data.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.tomasflopes.musicplayer.core.model.ports.PlayHistoryReader
import dev.tomasflopes.musicplayer.core.model.stats.ListeningAggregates
import dev.tomasflopes.musicplayer.core.taste.config.StatsConfig
import dev.tomasflopes.musicplayer.core.taste.config.TasteConfig
import dev.tomasflopes.musicplayer.core.taste.stats.ComebackArtist
import dev.tomasflopes.musicplayer.core.taste.stats.MostSkipped
import dev.tomasflopes.musicplayer.core.taste.stats.PeriodCalendar
import dev.tomasflopes.musicplayer.core.taste.stats.PeriodStatsSource
import dev.tomasflopes.musicplayer.core.taste.stats.StatsComputer
import dev.tomasflopes.musicplayer.data.db.MusicDatabase
import dev.tomasflopes.musicplayer.data.repo.RoomListeningAggregates
import kotlinx.coroutines.Dispatchers
import java.time.Clock
import java.time.ZoneId
import javax.inject.Singleton

/** What the Stats tab needs (ADR 0088): the Room aggregates and the one source that caches closed periods. */
@Module
@InstallIn(SingletonComponent::class)
object StatsDataModule {
    @Provides fun statsConfig(): StatsConfig = StatsConfig()

    @Provides @Singleton
    fun listeningAggregates(db: MusicDatabase): ListeningAggregates = RoomListeningAggregates(db)

    /**
     * One instance for the process, so the cache of closed periods is shared by every screen. Periods are the
     * owner's local months and years, so the zone is the phone's, read once here; the app's own clock is UTC.
     */
    @Provides @Singleton
    fun periodStatsSource(
        aggregates: ListeningAggregates,
        history: PlayHistoryReader,
        tasteConfig: TasteConfig,
        config: StatsConfig,
        clock: Clock,
    ): PeriodStatsSource {
        val zone = ZoneId.systemDefault()
        return PeriodStatsSource(
            computer =
                StatsComputer(
                    aggregates,
                    zone,
                    config,
                    MostSkipped(tasteConfig, config),
                    ComebackArtist(config),
                ),
            history = history,
            aggregates = aggregates,
            calendar = PeriodCalendar(zone),
            clock = clock.withZone(zone),
            config = config,
            dispatcher = Dispatchers.Default,
        )
    }
}
