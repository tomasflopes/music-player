package dev.tomasflopes.musicplayer.core.taste.stats

import dev.tomasflopes.musicplayer.core.model.stats.ListeningAggregates
import dev.tomasflopes.musicplayer.core.taste.config.StatsConfig
import java.time.ZoneId

/**
 * Builds the [PeriodStats] of one period from the aggregate queries (ADR 0088). A period with no plays stops after
 * the first query, so an empty month costs one read. Rows are read for one rule only: most skipped.
 */
class StatsComputer(
    private val aggregates: ListeningAggregates,
    private val zone: ZoneId,
    private val config: StatsConfig,
    private val mostSkipped: MostSkipped,
    private val comebackArtist: ComebackArtist,
) {
    suspend fun compute(period: StatsPeriod): PeriodStats {
        val range = period.range(zone)
        val clock = ListeningClock.from(aggregates.playsPerBucket(range), zone)
        if (clock.total == 0) return PeriodStats.empty(period)
        val all = ListeningAggregates.ALL
        return PeriodStats(
            period = period,
            totalPlays = clock.total,
            topSongs = aggregates.songPlays(range, config.topSize),
            topArtists = aggregates.artistPlays(range, config.topSize),
            topAlbums = aggregates.albumPlays(range, config.topSize),
            listenedMs = aggregates.listenedMs(range),
            newDiscoveries =
                NewDiscoveries(
                    aggregates.firstPlayCount(range),
                    aggregates.firstPlays(range, config.topSize),
                ),
            mostSkipped = mostSkipped.of(aggregates.playsInOrder(range)),
            clock = clock,
            comeback =
                comebackArtist.of(
                    current = aggregates.artistPlays(range, all),
                    previous = aggregates.artistPlays(period.previous().range(zone), all),
                ),
        )
    }
}
