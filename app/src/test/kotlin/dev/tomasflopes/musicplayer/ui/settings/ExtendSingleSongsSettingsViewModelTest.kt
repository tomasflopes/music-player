package dev.tomasflopes.musicplayer.ui.settings

import dev.tomasflopes.musicplayer.fakes.FakeSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ExtendSingleSongsSettingsViewModelTest {
    private val settings = FakeSettingsRepository()

    @BeforeEach
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `starts on`() =
        runTest {
            assertTrue(ExtendSingleSongsSettingsViewModel(settings).enabled.value)
        }

    @Test
    fun `the switch is saved`() =
        runTest {
            val viewModel = ExtendSingleSongsSettingsViewModel(settings)

            viewModel.onEnabled(false)
            assertFalse(settings.extendSingleSongsOn.value)

            viewModel.onEnabled(true)
            assertTrue(settings.extendSingleSongsOn.value)
        }
}
