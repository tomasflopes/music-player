package dev.tomasflopes.musicplayer.ui.stats

import dev.tomasflopes.musicplayer.core.model.SongId
import dev.tomasflopes.musicplayer.core.model.log.EndedBy
import dev.tomasflopes.musicplayer.core.model.log.PlayEvent
import dev.tomasflopes.musicplayer.core.model.log.PlaySource
import dev.tomasflopes.musicplayer.core.model.log.StartedBy
import dev.tomasflopes.musicplayer.core.model.ports.PlayHistoryReader
import dev.tomasflopes.musicplayer.core.model.stats.ContractSong
import dev.tomasflopes.musicplayer.core.model.stats.InMemoryListeningAggregates
import dev.tomasflopes.musicplayer.core.taste.config.StatsConfig
import dev.tomasflopes.musicplayer.core.taste.config.TasteConfig
import dev.tomasflopes.musicplayer.core.taste.stats.ComebackArtist
import dev.tomasflopes.musicplayer.core.taste.stats.MostSkipped
import dev.tomasflopes.musicplayer.core.taste.stats.PeriodCalendar
import dev.tomasflopes.musicplayer.core.taste.stats.PeriodStatsSource
import dev.tomasflopes.musicplayer.core.taste.stats.StatsComputer
import dev.tomasflopes.musicplayer.core.taste.stats.StatsPeriod
import dev.tomasflopes.musicplayer.fakes.FakeAlbumReader
import dev.tomasflopes.musicplayer.fakes.FakeArtistReader
import dev.tomasflopes.musicplayer.fakes.FakeSongReader
import dev.tomasflopes.musicplayer.fakes.testAlbum
import dev.tomasflopes.musicplayer.fakes.testArtist
import dev.tomasflopes.musicplayer.fakes.testSong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

private class LogSize(
    val size: MutableStateFlow<Int> = MutableStateFlow(0),
) : PlayHistoryReader {
    override fun observeRecent(limit: Int): Flow<List<PlayEvent>> = flowOf(emptyList())

    override fun observeCount(): Flow<Int> = size
}

@OptIn(ExperimentalCoroutinesApi::class)
class StatsViewModelTest {
    private val utc = ZoneId.of("UTC")
    private val library = listOf(ContractSong(1, 10, 100))

    @BeforeEach
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    private fun play(iso: String) =
        PlayEvent(
            SongId(1),
            Instant.parse(iso).toEpochMilli(),
            200_000,
            200_000,
            EndedBy.COMPLETED,
            PlaySource.SHUFFLE_ALL,
            StartedBy.AUTO,
            true,
        )

    private fun viewModel(
        vararg plays: PlayEvent,
        cards: Set<WrappedCard> = emptySet(),
    ): StatsViewModel {
        val aggregates = InMemoryListeningAggregates(library, plays.toList())
        val config = StatsConfig()
        val source =
            PeriodStatsSource(
                StatsComputer(aggregates, utc, config, MostSkipped(TasteConfig(), config), ComebackArtist(config)),
                LogSize(),
                aggregates,
                PeriodCalendar(utc),
                Clock.fixed(Instant.parse("2026-11-22T10:00:00Z"), utc),
                config,
                UnconfinedTestDispatcher(),
            )
        val resolver =
            WrappedDataResolver(
                FakeSongReader(songs = listOf(testSong(1))),
                FakeArtistReader(listOf(testArtist(10, "Ten"))),
                FakeAlbumReader(listOf(testAlbum(100, "Hundred", "Ten"))),
            )
        return StatsViewModel(source, resolver, cards)
    }

    private fun <T> TestScope.subscribed(flow: StateFlow<T>): StateFlow<T> {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { flow.collect {} }
        return flow
    }

    @Test
    fun `the Stats tab opens on this month`() = assertEquals(StatsView.THIS_MONTH, viewModel().view.value)

    @Test
    fun `selecting a view switches it`() {
        val vm = viewModel()

        vm.select(StatsView.YEARS)

        assertEquals(StatsView.YEARS, vm.view.value)
    }

    @Test
    fun `this month shows the plays of the current month`() =
        runTest {
            val vm = viewModel(play("2026-11-05T10:00:00Z"), play("2026-10-05T10:00:00Z"))

            val state = subscribed(vm.thisMonth).value

            assertEquals(1, assertInstanceOf(WrappedState.Ready::class.java, state).data.stats.totalPlays)
        }

    @Test
    fun `an empty log gives an empty month and no past periods`() =
        runTest {
            val vm = viewModel()

            val state = subscribed(vm.thisMonth).value

            assertTrue(assertInstanceOf(WrappedState.Ready::class.java, state).data.isEmpty)
            assertEquals(emptyList<StatsPeriod>(), subscribed(vm.pastMonths).value)
            assertEquals(emptyList<StatsPeriod>(), subscribed(vm.years).value)
        }

    @Test
    fun `past months and years list the periods since the first play, newest first`() =
        runTest {
            val vm = viewModel(play("2025-12-15T10:00:00Z"))

            assertEquals(StatsPeriod.Month(YearMonth.of(2026, 10)), subscribed(vm.pastMonths).value?.first())
            assertEquals(listOf(StatsPeriod.Year(2026), StatsPeriod.Year(2025)), subscribed(vm.years).value)
        }
}
