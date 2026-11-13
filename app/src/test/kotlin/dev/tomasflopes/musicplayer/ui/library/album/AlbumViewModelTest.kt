package dev.tomasflopes.musicplayer.ui.library.album

import dev.tomasflopes.musicplayer.core.model.AlbumId
import dev.tomasflopes.musicplayer.core.model.AlbumOrder
import dev.tomasflopes.musicplayer.core.model.log.PlaySource
import dev.tomasflopes.musicplayer.core.model.ref
import dev.tomasflopes.musicplayer.fakes.FakeAlbumReader
import dev.tomasflopes.musicplayer.fakes.FakePlaybackController
import dev.tomasflopes.musicplayer.fakes.FakeSongReader
import dev.tomasflopes.musicplayer.fakes.testAlbum
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
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AlbumViewModelTest {
    private val album = testAlbum(10, "Two Discs", "Sample", coverSongId = 21)

    // Out of tag order on purpose; the reader's contract is AlbumOrder, so the fake is given them sorted.
    private val discOneTrackOne = testSong(21, albumId = 10, trackNumber = 1, discNumber = 1, hasCover = true)
    private val discOneTrackTwo = testSong(22, albumId = 10, trackNumber = 2, discNumber = 1)
    private val discTwoTrackOne = testSong(23, albumId = 10, trackNumber = 1, discNumber = 2)
    private val twoDiscs =
        listOf(discTwoTrackOne, discOneTrackTwo, discOneTrackOne).sortedWith(AlbumOrder)

    private val noDiscTags =
        listOf(testSong(31, albumId = 11, trackNumber = 1), testSong(32, albumId = 11, trackNumber = 2))
    private val mixedDisc1 =
        listOf(testSong(41, albumId = 12, trackNumber = 1, discNumber = 1), testSong(42, albumId = 12, trackNumber = 2))

    private val playback = FakePlaybackController()
    private val songs =
        FakeSongReader(
            albumSongs =
                mapOf(AlbumId(10) to twoDiscs, AlbumId(11) to noDiscTags, AlbumId(12) to mixedDisc1),
        )
    private val albums =
        FakeAlbumReader(
            listOf(
                album,
                testAlbum(11, "No Disc Tags", "Sample"),
                testAlbum(12, "Disc 1 Only", "Sample"),
            ),
        )

    @BeforeEach
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    /** Opens [id] and keeps its state subscribed, as the screen does, so `value` follows the album. */
    private fun TestScope.open(id: Long = 10): AlbumViewModel {
        val viewModel = AlbumViewModel(playback, songs, albums)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }
        viewModel.open(AlbumId(id))
        return viewModel
    }

    private fun AlbumViewModel.ready() = assertInstanceOf(AlbumUiState.Ready::class.java, state.value)

    @Test
    fun `shows the album, its cover song and the tracks in album order`() =
        runTest {
            val ready = open().ready()

            assertEquals(album, ready.album)
            assertEquals(discOneTrackOne, ready.cover)
            assertEquals(listOf(discOneTrackOne, discOneTrackTwo, discTwoTrackOne), ready.songs)
        }

    @Test
    fun `an album with no cover song shows no cover`() =
        runTest {
            assertNull(open(id = 11).ready().cover)
        }

    @Test
    fun `Play sends the ids in album order, never shuffled`() =
        runTest {
            val viewModel = open()

            viewModel.onPlay()

            val refs = twoDiscs.map { it.ref }
            assertEquals(
                listOf(PlayRequest(refs, 0, shuffle = false, PlaySource.ALBUM, picked = false)),
                playback.requests,
            )
        }

    @Test
    fun `Shuffle sends the same songs with shuffle on, as an album`() =
        runTest {
            val viewModel = open()

            viewModel.onShuffle()

            assertEquals(
                listOf(PlayRequest(twoDiscs.map { it.ref }, 0, shuffle = true, PlaySource.ALBUM, picked = false)),
                playback.requests,
            )
        }

    @Test
    fun `tapping a track plays the album in order from it, as picked`() =
        runTest {
            val viewModel = open()

            viewModel.onTrackClick(2)

            assertEquals(
                listOf(PlayRequest(twoDiscs.map { it.ref }, 2, shuffle = false, PlaySource.ALBUM, picked = true)),
                playback.requests,
            )
        }

    @Test
    fun `a track index outside the album plays nothing`() =
        runTest {
            val viewModel = open()

            viewModel.onTrackClick(3)
            viewModel.onTrackClick(-1)

            assertTrue(playback.requests.isEmpty())
        }

    @Test
    fun `an album with two discs has a header before each disc`() =
        runTest {
            val entries = open().ready().entries

            assertEquals(
                listOf(
                    AlbumEntry.DiscHeader(1),
                    AlbumEntry.Track(0, discOneTrackOne),
                    AlbumEntry.Track(1, discOneTrackTwo),
                    AlbumEntry.DiscHeader(2),
                    AlbumEntry.Track(2, discTwoTrackOne),
                ),
                entries,
            )
        }

    @Test
    fun `an album without disc tags has no disc headers`() =
        runTest {
            val entries = open(id = 11).ready().entries

            assertEquals(noDiscTags.mapIndexed { index, song -> AlbumEntry.Track(index, song) }, entries)
        }

    @Test
    fun `a missing disc tag counts as disc 1, so one tagged disc 1 shows no headers`() =
        runTest {
            val entries = open(id = 12).ready().entries

            assertTrue(entries.none { it is AlbumEntry.DiscHeader })
        }

    @Test
    fun `an album that does not exist shows not found`() =
        runTest {
            val viewModel = open(id = 99)

            assertEquals(AlbumUiState.NotFound, viewModel.state.value)
        }

    @Test
    fun `Play next and Add to queue send that one song`() =
        runTest {
            val viewModel = open()

            viewModel.onPlayNext(discOneTrackTwo)
            viewModel.onAddToQueue(discTwoTrackOne)

            assertEquals(listOf(listOf(discOneTrackTwo.ref)), playback.playNextCalls)
            assertEquals(listOf(listOf(discTwoTrackOne.ref)), playback.addToQueueCalls)
        }
}
