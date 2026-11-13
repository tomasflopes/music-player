package dev.tomasflopes.musicplayer.ui.library.albums

import dev.tomasflopes.musicplayer.core.model.Section
import dev.tomasflopes.musicplayer.fakes.FakeAlbumReader
import dev.tomasflopes.musicplayer.fakes.FakeSongReader
import dev.tomasflopes.musicplayer.fakes.testAlbum
import dev.tomasflopes.musicplayer.fakes.testSong
import dev.tomasflopes.musicplayer.ui.library.IndexedList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.filterNotNull
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
class AlbumsViewModelTest {
    private val cover = testSong(100, albumId = 10, hasCover = true)

    // In sort-key order: "The Dark Side" sorts under D.
    private val damn = testAlbum(10, "DAMN.", "Kendrick Lamar", coverSongId = 100)
    private val darkSide = testAlbum(11, "The Dark Side", "Pink Floyd")
    private val zen = testAlbum(12, "Zen", "Sample")
    private val library = listOf(damn, darkSide, zen)

    private val songs = FakeSongReader(songs = listOf(cover))

    @BeforeEach
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = AlbumsViewModel(songs, FakeAlbumReader(library))

    private suspend fun AlbumsViewModel.loaded(): IndexedList<AlbumListItem> = albums.filterNotNull().first()

    @Test
    fun `sections match the sort keys, so The Dark Side is under D`() =
        runTest {
            val shown = viewModel().loaded()

            assertEquals(listOf(Section('D', 0), Section('Z', 2)), shown.sections)
            assertEquals(library, shown.items.map { it.album })
        }

    @Test
    fun `an album's cover is the song its coverSongId names, or none`() =
        runTest {
            val shown = viewModel().loaded()

            assertEquals(cover, shown.items[0].cover)
            assertNull(shown.items[1].cover)
        }
}
