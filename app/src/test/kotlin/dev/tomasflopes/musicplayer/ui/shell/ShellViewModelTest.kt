package dev.tomasflopes.musicplayer.ui.shell

import dev.tomasflopes.musicplayer.core.model.ports.MusicRoot
import dev.tomasflopes.musicplayer.core.model.ports.MusicRootResolver
import dev.tomasflopes.musicplayer.fakes.FakeScanController
import dev.tomasflopes.musicplayer.fakes.FakeSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ShellViewModelTest {
    private val settings = FakeSettingsRepository()
    private val scanner = FakeScanController()
    private val resolver =
        object : MusicRootResolver {
            override fun resolve(
                treeUri: String,
                treeDocumentId: String,
            ): MusicRoot? = if (':' in treeDocumentId) MusicRoot(treeUri, "external_primary", "Music/") else null
        }

    private fun viewModel() = ShellViewModel(settings, resolver, scanner)

    @BeforeEach
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `a picked folder is saved and scanned once`() {
        viewModel().folderPicked("content://tree", "primary:Music")

        assertEquals(MusicRoot("content://tree", "external_primary", "Music/"), settings.root.value)
        assertEquals(1, scanner.scanRequests)
    }

    @Test
    fun `picking another folder later replaces the saved one and scans again`() {
        val viewModel = viewModel()
        viewModel.folderPicked("content://first", "primary:Music")

        viewModel.folderPicked("content://second", "1A2B-3C4D:Songs")

        assertEquals(MusicRoot("content://second", "external_primary", "Music/"), settings.root.value)
        assertEquals(2, scanner.scanRequests)
    }

    @Test
    fun `a folder MediaStore does not list changes nothing`() {
        viewModel().folderPicked("content://tree", "downloads")

        assertNull(settings.root.value)
        assertEquals(0, scanner.scanRequests)
    }

    @Test
    fun `the prompt shows until a folder is saved, then the tabs`() =
        runTest {
            val viewModel = viewModel()
            assertEquals(ShellState.NEEDS_FOLDER, viewModel.state.first { it != ShellState.LOADING })

            viewModel.folderPicked("content://tree", "primary:Music")

            assertEquals(ShellState.READY, viewModel.state.first { it == ShellState.READY })
        }
}
