package dev.tomasflopes.musicplayer.ui.settings

import dev.tomasflopes.musicplayer.core.model.config.LibraryConfig
import dev.tomasflopes.musicplayer.core.model.lists.DynamicListProvider
import dev.tomasflopes.musicplayer.core.model.ports.MusicRoot
import dev.tomasflopes.musicplayer.core.taste.lists.RecentlyAddedProvider
import dev.tomasflopes.musicplayer.fakes.FakePlaybackController
import dev.tomasflopes.musicplayer.fakes.FakeSettingsEntry
import dev.tomasflopes.musicplayer.fakes.FakeSettingsRepository
import dev.tomasflopes.musicplayer.fakes.FakeSongReader
import dev.tomasflopes.musicplayer.ui.home.dynamiclist.DaysError
import dev.tomasflopes.musicplayer.ui.home.dynamiclist.DynamicListUiState
import dev.tomasflopes.musicplayer.ui.home.dynamiclist.DynamicListViewModel
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
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Clock

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val config = LibraryConfig()
    private val settings = FakeSettingsRepository()

    @BeforeEach
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(vararg entries: FakeSettingsEntry) = SettingsViewModel(settings, config, entries.toSet())

    /** Keeps the state subscribed, as the screen does, so `value` follows the settings. */
    private fun TestScope.subscribed(viewModel: SettingsViewModel): SettingsViewModel {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }
        return viewModel
    }

    @Test
    fun `no folder is shown until one is saved, then its path`() =
        runTest {
            val viewModel = subscribed(viewModel())
            assertNull(viewModel.state.value.musicFolder)

            settings.root.value = MusicRoot("content://tree", "external_primary", "Music/")

            assertEquals("Internal storage/Music", viewModel.state.value.musicFolder)
        }

    @Test
    fun `the folder shown follows a change of the saved root`() =
        runTest {
            settings.root.value = MusicRoot("content://old", "external_primary", "Music/")
            val viewModel = subscribed(viewModel())

            settings.setMusicRoot(MusicRoot("content://new", "external_primary", "Music/HipHop/"))

            assertEquals("Internal storage/Music/HipHop", viewModel.state.value.musicFolder)
        }

    @Test
    fun `the window shows the saved days and the quick options`() =
        runTest {
            settings.days.value = 15
            val window = subscribed(viewModel()).state.value.recentlyAdded

            assertEquals(15, window.days)
            assertEquals(config.recentlyAddedQuickOptions, window.quickOptions)
            assertEquals(config.recentlyAddedDaysRange, window.range)
            assertNull(window.error)
        }

    @Test
    fun `choosing a quick option saves it`() =
        runTest {
            val viewModel = subscribed(viewModel())

            assertTrue(viewModel.onQuickDays(60))

            assertEquals(listOf(60), settings.savedDays)
            assertEquals(60, viewModel.state.value.recentlyAdded.days)
        }

    @Test
    fun `a custom value inside the range is saved`() =
        runTest {
            val viewModel = subscribed(viewModel())

            assertTrue(viewModel.onCustomDays(" 45 "))

            assertEquals(listOf(45), settings.savedDays)
            assertNull(viewModel.state.value.recentlyAdded.error)
        }

    @Test
    fun `custom 0 and 5000 are rejected, nothing is saved and the error shows`() =
        runTest {
            val viewModel = subscribed(viewModel())

            assertFalse(viewModel.onCustomDays("0"))
            assertEquals(DaysError.OUT_OF_RANGE, viewModel.state.value.recentlyAdded.error)
            assertFalse(viewModel.onCustomDays("5000"))
            assertEquals(DaysError.OUT_OF_RANGE, viewModel.state.value.recentlyAdded.error)

            assertTrue(settings.savedDays.isEmpty())
            assertEquals(20, viewModel.state.value.recentlyAdded.days)
        }

    @Test
    fun `text that is not a number is rejected and the error clears when the picker closes`() =
        runTest {
            val viewModel = subscribed(viewModel())

            assertFalse(viewModel.onCustomDays("abc"))
            assertEquals(DaysError.NOT_A_NUMBER, viewModel.state.value.recentlyAdded.error)
            viewModel.onPickerDismissed()

            assertNull(viewModel.state.value.recentlyAdded.error)
            assertTrue(settings.savedDays.isEmpty())
        }

    @Test
    fun `the days edited in Settings are the days the Recently added list shows`() =
        runTest {
            val settingsScreen = subscribed(viewModel())
            val listScreen =
                DynamicListViewModel(
                    FakePlaybackController(),
                    setOf<DynamicListProvider>(RecentlyAddedProvider(FakeSongReader(), settings, Clock.systemUTC())),
                    settings,
                )
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { listScreen.state.collect {} }
            listScreen.open(RecentlyAddedProvider.ID)

            settingsScreen.onQuickDays(30)

            assertEquals(30, (listScreen.state.value as DynamicListUiState.Ready).windowDays)
            assertEquals(listOf(30), settings.savedDays)
        }

    @Test
    fun `entries are listed by order then id, so a new one needs no Settings code`() {
        val viewModel =
            viewModel(
                FakeSettingsEntry("b", order = 2),
                FakeSettingsEntry("c", order = 1),
                FakeSettingsEntry("a", order = 2),
            )

        assertEquals(listOf("c", "a", "b"), viewModel.entries.map { it.id })
    }

    @Test
    fun `two entries with the same id are refused`() {
        assertThrows(IllegalArgumentException::class.java) {
            SettingsViewModel(
                settings,
                config,
                setOf(FakeSettingsEntry("debug"), FakeSettingsEntry("debug", order = 1)),
            )
        }
    }
}
