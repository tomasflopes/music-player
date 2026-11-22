package dev.tomasflopes.musicplayer.ui.stats

import androidx.lifecycle.SavedStateHandle
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
import dev.tomasflopes.musicplayer.fakes.testSong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
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
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

private object NoChanges : PlayHistoryReader {
    override fun observeRecent(limit: Int): Flow<List<PlayEvent>> = flowOf(emptyList())

    override fun observeCount(): Flow<Int> = flowOf(0)
}

@OptIn(ExperimentalCoroutinesApi::class)
class WrappedViewModelTest {
    private val utc = ZoneId.of("UTC")

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

    private fun viewModel(key: String?): WrappedViewModel {
        val aggregates =
            InMemoryListeningAggregates(
                listOf(ContractSong(1, 10, 100)),
                listOf(play("2026-10-05T10:00:00Z"), play("2026-10-06T10:00:00Z"), play("2026-11-05T10:00:00Z")),
            )
        val config = StatsConfig()
        val source =
            PeriodStatsSource(
                StatsComputer(aggregates, utc, config, MostSkipped(TasteConfig(), config), ComebackArtist(config)),
                NoChanges,
                aggregates,
                PeriodCalendar(utc),
                Clock.fixed(Instant.parse("2026-11-22T10:00:00Z"), utc),
                config,
                UnconfinedTestDispatcher(),
            )
        val resolver =
            WrappedDataResolver(FakeSongReader(songs = listOf(testSong(1))), FakeArtistReader(), FakeAlbumReader())
        return WrappedViewModel(
            SavedStateHandle(
                listOfNotNull(
                    key?.let {
                        WrappedRoute.ARG_KEY to it
                    },
                ).toMap(),
            ),
            source,
            resolver,
            emptySet(),
        )
    }

    private fun <T> TestScope.subscribed(flow: StateFlow<T>): StateFlow<T> {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { flow.collect {} }
        return flow
    }

    @Test
    fun `the route key picks the period`() =
        runTest {
            val vm = viewModel("2026-10")

            val ready = assertInstanceOf(WrappedState.Ready::class.java, subscribed(vm.state).value)

            assertEquals(StatsPeriod.Month(YearMonth.of(2026, 10)), vm.period)
            assertEquals(2, ready.data.stats.totalPlays)
        }

    @Test
    fun `a key that is not a period falls back to this month`() =
        runTest {
            val vm = viewModel("nonsense")

            val ready = assertInstanceOf(WrappedState.Ready::class.java, subscribed(vm.state).value)

            assertEquals(StatsPeriod.Month(YearMonth.of(2026, 11)), vm.period)
            assertEquals(1, ready.data.stats.totalPlays)
        }
}
