package dev.tomasflopes.musicplayer.ui.library.folders

import dev.tomasflopes.musicplayer.core.model.Folder
import dev.tomasflopes.musicplayer.core.model.log.PlaySource
import dev.tomasflopes.musicplayer.core.model.ports.MusicRoot
import dev.tomasflopes.musicplayer.core.model.ref
import dev.tomasflopes.musicplayer.fakes.FakeFolderReader
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

@OptIn(ExperimentalCoroutinesApi::class)
class FoldersViewModelTest {
    private val rock = Folder("Music/Rock", "Rock", parentPath = null, songCount = 4)
    private val jazz = Folder("Music/Jazz", "Jazz", parentPath = null, songCount = 0)
    private val live = Folder("Music/Rock/Live", "Live", parentPath = "Music/Rock", songCount = 1)

    private val rootSong = testSong(9)
    private val rockSongs = listOf(testSong(1), testSong(2), testSong(3))
    private val liveSongs = listOf(testSong(4))

    private val playback = FakePlaybackController()
    private val folders =
        FakeFolderReader(
            mapOf(
                null to listOf(jazz, rock),
                rock.path to listOf(live),
            ),
        )
    private val songs =
        FakeSongReader(
            folderSongs =
                mapOf(
                    "Music" to listOf(rootSong),
                    rock.path to rockSongs,
                    live.path to liveSongs,
                ),
        )
    private val settings =
        FakeSettingsRepository().apply { root.value = MusicRoot("tree", "external_primary", "Music/") }

    @BeforeEach
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    /** Keeps the state subscribed, as the screen does, so `value` follows the navigation. */
    private fun TestScope.viewModel(): FoldersViewModel {
        val viewModel = FoldersViewModel(playback, folders, songs, settings)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }
        return viewModel
    }

    private fun FoldersViewModel.shown() = checkNotNull(state.value) { "state is still loading" }

    @Test
    fun `the root shows the music folder's children and the songs directly in it`() =
        runTest {
            val shown = viewModel().shown()

            assertNull(shown.current)
            assertEquals(listOf(jazz, rock), shown.folders)
            assertEquals(listOf(rootSong), shown.songs)
        }

    @Test
    fun `entering a folder shows its children and its songs`() =
        runTest {
            val viewModel = viewModel()

            viewModel.onOpenFolder(rock)

            val shown = viewModel.shown()
            assertEquals(rock, shown.current)
            assertEquals(listOf(live), shown.folders)
            assertEquals(rockSongs, shown.songs)
        }

    @Test
    fun `back goes up one level at a time and restores the parent`() =
        runTest {
            val viewModel = viewModel()
            viewModel.onOpenFolder(rock)
            viewModel.onOpenFolder(live)
            assertEquals(live, viewModel.shown().current)

            assertTrue(viewModel.onBack())
            assertEquals(rock, viewModel.shown().current)
            assertEquals(listOf(live), viewModel.shown().folders)
            assertEquals(rockSongs, viewModel.shown().songs)

            assertTrue(viewModel.onBack())
            assertNull(viewModel.shown().current)
            assertEquals(listOf(jazz, rock), viewModel.shown().folders)
        }

    @Test
    fun `back at the root is not handled, so the system goes on as usual`() =
        runTest {
            val viewModel = viewModel()

            assertFalse(viewModel.onBack())
            assertNull(viewModel.shown().current)
        }

    @Test
    fun `Play sends the folder's songs in display order, never shuffled`() =
        runTest {
            val viewModel = viewModel()
            viewModel.onOpenFolder(rock)

            viewModel.onPlay()

            assertEquals(
                listOf(PlayRequest(rockSongs.map { it.ref }, 0, shuffle = false, PlaySource.FOLDER, picked = false)),
                playback.requests,
            )
        }

    @Test
    fun `tapping the third song plays the folder in order from it, as picked`() =
        runTest {
            val viewModel = viewModel()
            viewModel.onOpenFolder(rock)

            viewModel.onSongClick(2)

            assertEquals(
                listOf(PlayRequest(rockSongs.map { it.ref }, 2, shuffle = false, PlaySource.FOLDER, picked = true)),
                playback.requests,
            )
        }

    @Test
    fun `Shuffle sends the folder's songs with shuffle on and nothing picked`() =
        runTest {
            val viewModel = viewModel()
            viewModel.onOpenFolder(rock)

            viewModel.onShuffle()

            assertEquals(
                listOf(PlayRequest(rockSongs.map { it.ref }, 0, shuffle = true, PlaySource.FOLDER, picked = false)),
                playback.requests,
            )
        }

    @Test
    fun `Play on the root sends only the songs directly in it, not those of its subfolders`() =
        runTest {
            val viewModel = viewModel()

            viewModel.onPlay()

            assertEquals(
                listOf(PlayRequest(listOf(rootSong.ref), 0, shuffle = false, PlaySource.FOLDER, picked = false)),
                playback.requests,
            )
        }

    @Test
    fun `a folder with no songs of its own plays nothing`() =
        runTest {
            val viewModel = viewModel()
            viewModel.onOpenFolder(jazz)

            viewModel.onPlay()
            viewModel.onShuffle()
            viewModel.onSongClick(0)

            assertTrue(playback.requests.isEmpty())
        }

    @Test
    fun `a song index outside the folder plays nothing`() =
        runTest {
            val viewModel = viewModel()
            viewModel.onOpenFolder(rock)

            viewModel.onSongClick(3)
            viewModel.onSongClick(-1)

            assertTrue(playback.requests.isEmpty())
        }

    @Test
    fun `Play next and Add to queue carry just that song`() =
        runTest {
            val viewModel = viewModel()

            viewModel.onPlayNext(rootSong)
            viewModel.onAddToQueue(rootSong)

            assertEquals(listOf(listOf(rootSong.ref)), playback.playNextCalls)
            assertEquals(listOf(listOf(rootSong.ref)), playback.addToQueueCalls)
        }
}
