package dev.tomasflopes.musicplayer.ui.home.dynamiclist

import dev.tomasflopes.musicplayer.core.model.lists.DynamicListProvider
import dev.tomasflopes.musicplayer.core.model.log.PlaySource
import dev.tomasflopes.musicplayer.core.model.ref
import dev.tomasflopes.musicplayer.core.taste.lists.RecentlyAddedProvider
import dev.tomasflopes.musicplayer.fakes.FakeDynamicListProvider
import dev.tomasflopes.musicplayer.fakes.FakePlaybackController
import dev.tomasflopes.musicplayer.fakes.FakeSettingsRepository
import dev.tomasflopes.musicplayer.fakes.FakeSongReader
import dev.tomasflopes.musicplayer.fakes.testSong
import dev.tomasflopes.musicplayer.playback.PlayRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class DynamicListViewModelTest {
    private val now = Instant.parse("2026-11-13T12:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val playback = FakePlaybackController()
    private val settings = FakeSettingsRepository()

    // Added 5, 18 and 25 days ago, and one 400 days ago; the default window is 20 days.
    private val fiveDays = testSong(1, dateAddedEpochSec = daysAgo(5))
    private val eighteenDays = testSong(2, dateAddedEpochSec = daysAgo(18))
    private val twentyFiveDays = testSong(3, dateAddedEpochSec = daysAgo(25))
    private val old = testSong(4, dateAddedEpochSec = daysAgo(400))
    private val library = FakeSongReader(songs = listOf(old, twentyFiveDays, fiveDays, eighteenDays))

    @BeforeEach
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    private fun daysAgo(days: Long) = now.epochSecond - days * SECONDS_PER_DAY

    private fun viewModel(vararg extra: FakeDynamicListProvider) =
        DynamicListViewModel(
            playback,
            setOf<DynamicListProvider>(RecentlyAddedProvider(library, settings, clock)) + extra,
            settings,
        )

    /** Opens [id] and keeps its state subscribed, as the screen does, so `value` follows the list. */
    private fun TestScope.open(
        viewModel: DynamicListViewModel,
        id: String = RecentlyAddedProvider.ID,
    ): DynamicListViewModel {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }
        viewModel.open(id)
        return viewModel
    }

    private fun DynamicListViewModel.ready() = state.value as DynamicListUiState.Ready

    @Test
    fun `shows the list's title, its songs newest first and the window from settings`() =
        runTest {
            val viewModel = open(viewModel())

            val ready = viewModel.ready()
            assertEquals("Recently added", ready.title)
            assertEquals(listOf(fiveDays, eighteenDays), ready.songs)
            assertEquals(20, ready.windowDays)
        }

    @Test
    fun `a new window from Settings re-emits the list at once without the older song`() =
        runTest {
            val viewModel = open(viewModel())

            settings.days.value = 15

            assertEquals(15, viewModel.ready().windowDays)
            assertEquals(listOf(fiveDays), viewModel.ready().songs)
        }

    @Test
    fun `a wider window brings the older songs back`() =
        runTest {
            val viewModel = open(viewModel())

            settings.days.value = 30

            assertEquals(listOf(fiveDays, eighteenDays, twentyFiveDays), viewModel.ready().songs)
        }

    @Test
    fun `play sends the list in order, not shuffled, as recently added`() =
        runTest {
            val viewModel = open(viewModel())

            viewModel.onPlay()

            val refs = listOf(fiveDays, eighteenDays).map { it.ref }
            assertEquals(
                listOf(PlayRequest(refs, 0, shuffle = false, PlaySource.RECENTLY_ADDED, picked = false)),
                playback.requests,
            )
        }

    @Test
    fun `shuffle sends the same songs shuffled, as recently added`() =
        runTest {
            val viewModel = open(viewModel())

            viewModel.onShuffle()

            val refs = listOf(fiveDays, eighteenDays).map { it.ref }
            assertEquals(
                listOf(PlayRequest(refs, 0, shuffle = true, PlaySource.RECENTLY_ADDED, picked = false)),
                playback.requests,
            )
        }

    @Test
    fun `tapping the second song plays from it, as picked`() =
        runTest {
            val viewModel = open(viewModel())

            viewModel.onSongClick(1)

            val request = playback.requests.single()
            assertEquals(1, request.startIndex)
            assertFalse(request.shuffle)
            assertTrue(request.picked)
            assertEquals(PlaySource.RECENTLY_ADDED, request.source)
        }

    @Test
    fun `play, shuffle and a tap out of range do nothing when the list is empty`() =
        runTest {
            settings.days.value = 1
            val viewModel = open(viewModel())

            viewModel.onPlay()
            viewModel.onShuffle()
            viewModel.onSongClick(0)

            assertTrue(playback.requests.isEmpty())
        }

    @Test
    fun `another provider's list opens with its own source and no days window`() =
        runTest {
            val discover =
                FakeDynamicListProvider("discover", 5, "Discover", PlaySource.DISCOVER, listOf(testSong(7)))
            val viewModel = open(viewModel(discover), "discover")

            viewModel.onPlay()

            assertEquals("Discover", viewModel.ready().title)
            assertNull(viewModel.ready().windowDays)
            assertEquals(PlaySource.DISCOVER, playback.requests.single().source)
        }

    @Test
    fun `an unknown list id is reported, not crashed on`() =
        runTest {
            val viewModel = open(viewModel(), "nope")

            assertEquals(DynamicListUiState.NotFound, viewModel.state.value)
        }

    @Test
    fun `before a list is opened the state is loading`() =
        runTest {
            assertEquals(DynamicListUiState.Loading, viewModel().state.value)
        }

    private companion object {
        const val SECONDS_PER_DAY = 86_400L
    }
}
