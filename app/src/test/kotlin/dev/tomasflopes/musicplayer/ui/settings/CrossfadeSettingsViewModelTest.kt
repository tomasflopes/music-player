package dev.tomasflopes.musicplayer.ui.settings

import dev.tomasflopes.musicplayer.core.model.crossfade.CrossfadeSettings
import dev.tomasflopes.musicplayer.fakes.FakeSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CrossfadeSettingsViewModelTest {
    private val settings = FakeSettingsRepository()

    @BeforeEach
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `starts off at four seconds`() =
        runTest {
            val viewModel = CrossfadeSettingsViewModel(settings)

            assertEquals(CrossfadeSettings(enabled = false, durationMs = 4_000), viewModel.state.value)
        }

    @Test
    fun `the switch is saved and the length is kept`() =
        runTest {
            val viewModel = CrossfadeSettingsViewModel(settings)

            viewModel.onDurationSeconds(9)
            viewModel.onEnabled(true)

            assertEquals(CrossfadeSettings(enabled = true, durationMs = 9_000), settings.crossfadeSettings.value)
        }

    @Test
    fun `turning it off keeps the length for next time`() =
        runTest {
            val viewModel = CrossfadeSettingsViewModel(settings)
            viewModel.onEnabled(true)
            viewModel.onDurationSeconds(7)

            viewModel.onEnabled(false)

            assertEquals(CrossfadeSettings(enabled = false, durationMs = 7_000), settings.crossfadeSettings.value)
        }

    @Test
    fun `a length outside one to twelve seconds is pulled into range`() =
        runTest {
            val viewModel = CrossfadeSettingsViewModel(settings)

            viewModel.onDurationSeconds(40)
            assertEquals(12_000L, settings.crossfadeSettings.value.durationMs)

            viewModel.onDurationSeconds(0)
            assertEquals(1_000L, settings.crossfadeSettings.value.durationMs)
        }
}
