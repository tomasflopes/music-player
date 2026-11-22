package dev.tomasflopes.musicplayer.core.taste.stats

import dev.tomasflopes.musicplayer.core.model.log.PlayEvent
import dev.tomasflopes.musicplayer.core.model.ports.PlayHistoryReader
import dev.tomasflopes.musicplayer.core.model.stats.ContractSong
import dev.tomasflopes.musicplayer.core.model.stats.InMemoryListeningAggregates
import dev.tomasflopes.musicplayer.core.model.stats.ListeningAggregates
import dev.tomasflopes.musicplayer.core.model.stats.SongPlays
import dev.tomasflopes.musicplayer.core.model.stats.TimeRange
import dev.tomasflopes.musicplayer.core.taste.config.StatsConfig
import dev.tomasflopes.musicplayer.core.taste.config.TasteConfig
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.YearMonth

/** The log as a flow of its size, which is all the source listens to. */
private class FakeHistory(
    val size: MutableStateFlow<Int> = MutableStateFlow(0),
) : PlayHistoryReader {
    override fun observeRecent(limit: Int): Flow<List<PlayEvent>> = flowOf(emptyList())

    override fun observeCount(): Flow<Int> = size
}

/** Counts how many times a period's songs were read, which is one per computation. */
private class CountingAggregates(
    private val inner: InMemoryListeningAggregates,
) : ListeningAggregates by inner {
    var songReads = 0

    override suspend fun songPlays(
        range: TimeRange,
        limit: Int,
    ): List<SongPlays> {
        songReads++
        return inner.songPlays(range, limit)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class PeriodStatsSourceTest {
    private val library = listOf(ContractSong(1, 11, 101), ContractSong(2, 12, 102))
    private val october = StatsPeriod.Month(YearMonth.of(2026, 10))
    private val november = october.next()

    private class Setup(
        now: String,
        initial: List<PlayEvent>,
        library: List<ContractSong>,
    ) {
        val inner = InMemoryListeningAggregates(library, initial)
        val aggregates = CountingAggregates(inner)
        val history = FakeHistory()
        val clock: Clock = Clock.fixed(Instant.parse(now), UTC)
        val config = StatsConfig()
        val source =
            PeriodStatsSource(
                StatsComputer(aggregates, UTC, config, MostSkipped(TasteConfig(), config), ComebackArtist(config)),
                history,
                aggregates,
                PeriodCalendar(UTC),
                clock,
                config,
                UnconfinedTestDispatcher(),
            )
    }

    private fun setup(
        now: String = "2026-11-22T10:00:00Z",
        vararg rows: PlayEvent,
    ) = Setup(now, rows.toList(), library)

    @Test
    fun `a closed period is computed once for the life of the source`() =
        runTest {
            val s = setup(rows = arrayOf(row(1, epoch("2026-10-05T10:00:00Z"))))

            val first = s.source.observe(october).first()
            val second = s.source.observe(october).first()

            assertEquals(1, first.totalPlays)
            assertEquals(first, second)
            assertEquals(1, s.aggregates.songReads)
        }

    @Test
    fun `a closed period does not read the log again when the log changes`() =
        runTest {
            val s = setup(rows = arrayOf(row(1, epoch("2026-10-05T10:00:00Z"))))
            s.source.observe(october).first()

            s.inner.append(row(2, epoch("2026-11-10T10:00:00Z")))
            s.history.size.value = 2
            s.source.observe(october).first()

            assertEquals(1, s.aggregates.songReads)
        }

    @Test
    fun `the current period is computed again when the log changes`() =
        runTest {
            val s = setup(rows = arrayOf(row(1, epoch("2026-11-05T10:00:00Z"))))
            val seen = mutableListOf<Int>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                s.source.observe(november).collect { seen += it.totalPlays }
            }

            s.inner.append(row(2, epoch("2026-11-22T09:00:00Z")))
            s.history.size.value = 1

            assertEquals(listOf(1, 2), seen)
        }

    @Test
    fun `a period that has just ended is still recomputed until the grace has passed`() =
        runTest {
            val s = setup(now = "2026-12-01T00:30:00Z", rows = arrayOf(row(1, epoch("2026-11-30T23:59:50Z"))))

            s.source.observe(november).first()
            s.source.observe(november).first()

            assertEquals(2, s.aggregates.songReads)
        }

    @Test
    fun `past months and years come from the first play`() =
        runTest {
            val s = setup(rows = arrayOf(row(1, epoch("2025-12-15T10:00:00Z"))))

            assertEquals(november, s.source.currentMonth())
            assertEquals(
                listOf(10, 9, 8, 7, 6, 5, 4, 3, 2, 1, 12),
                s.source.pastMonths().map {
                    (it as StatsPeriod.Month).value.monthValue
                },
            )
            assertEquals(listOf(StatsPeriod.Year(2026), StatsPeriod.Year(2025)), s.source.years())
        }

    @Test
    fun `with an empty log there are no past months and no years`() =
        runTest {
            val s = setup()

            assertEquals(emptyList<StatsPeriod>(), s.source.pastMonths())
            assertEquals(emptyList<StatsPeriod>(), s.source.years())
        }

    @Test
    fun `the lists of periods follow the log, so a first play adds this year`() =
        runTest {
            val s = setup()
            val seen = mutableListOf<List<StatsPeriod>>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                s.source.observeYears().collect {
                    seen +=
                        it
                }
            }

            s.inner.append(row(1, epoch("2026-11-10T10:00:00Z")))
            s.history.size.value = 1

            assertEquals(listOf(emptyList(), listOf(StatsPeriod.Year(2026))), seen)
        }
}
