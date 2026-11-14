package dev.tomasflopes.musicplayer.ui.library.artist

import dev.tomasflopes.musicplayer.core.model.ArtistId
import dev.tomasflopes.musicplayer.core.model.Song
import dev.tomasflopes.musicplayer.core.model.log.PlaySource
import dev.tomasflopes.musicplayer.core.model.ref
import dev.tomasflopes.musicplayer.fakes.FakeAlbumReader
import dev.tomasflopes.musicplayer.fakes.FakeArtistReader
import dev.tomasflopes.musicplayer.fakes.FakePlaybackController
import dev.tomasflopes.musicplayer.fakes.FakeSongReader
import dev.tomasflopes.musicplayer.fakes.testAlbum
import dev.tomasflopes.musicplayer.fakes.testArtist
import dev.tomasflopes.musicplayer.fakes.testSong
import dev.tomasflopes.musicplayer.playback.PlayRequest
import dev.tomasflopes.musicplayer.ui.library.albums.AlbumListItem
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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ArtistViewModelTest {
    private val ed = testArtist(1, "Ed")
    private val loner = testArtist(2, "Loner", songCount = 0)

    private val cover = testSong(100, albumId = 10, artistId = 1, hasCover = true)
    private val morning = testSong(11, albumId = 10, artistId = 1, title = "Morning")
    private val night = testSong(12, albumId = null, artistId = 1, title = "Night")
    private val evening = testSong(13, albumId = null, artistId = 1, title = "Evening")

    // Songs by others that credit Ed in the title; never part of Ed's own queue.
    private val featA = testSong(21, albumId = 20, artistId = 3, artistName = "Kendrick", title = "A (feat. Ed)")
    private val featB = testSong(22, albumId = 20, artistId = 3, artistName = "Kendrick", title = "B (feat. Ed)")

    private val debut = testAlbum(10, "Debut", "Ed", coverSongId = 100)
    private val plain = testAlbum(11, "Plain", "Ed")

    private val ownSongs = listOf(morning, night, evening)
    private val playback = FakePlaybackController()
    private val songs =
        FakeSongReader(
            songs = listOf(cover, morning, night, evening, featA, featB),
            artistSongs = mapOf(ArtistId(1) to ownSongs),
            featuredOn = mapOf(ArtistId(1) to listOf(featA, featB)),
        )

    @BeforeEach
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.open(id: Long = 1): ArtistViewModel {
        val viewModel =
            ArtistViewModel(
                playback,
                songs,
                FakeArtistReader(listOf(ed, loner)),
                FakeAlbumReader(artistAlbums = mapOf(ArtistId(1) to listOf(debut, plain))),
            )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }
        viewModel.open(ArtistId(id))
        return viewModel
    }

    private fun ArtistViewModel.ready() = assertInstanceOf(ArtistUiState.Ready::class.java, state.value)

    private fun request(
        shown: List<Song>,
        startIndex: Int,
        shuffle: Boolean,
        source: PlaySource,
        picked: Boolean,
    ) = PlayRequest(shown.map { it.ref }, startIndex, shuffle, source, picked)

    @Test
    fun `shows the albums with their covers, own songs, then Featured on, each under its header`() =
        runTest {
            val entries = open().ready().entries

            assertEquals(
                listOf(
                    ArtistEntry.Header(ArtistSection.ALBUMS),
                    ArtistEntry.Album(AlbumListItem(debut, cover)),
                    ArtistEntry.Album(AlbumListItem(plain, null)),
                    ArtistEntry.Header(ArtistSection.SONGS),
                    ArtistEntry.Track(0, morning),
                    ArtistEntry.Track(1, night),
                    ArtistEntry.Track(2, evening),
                    ArtistEntry.Header(ArtistSection.FEATURED_ON),
                    ArtistEntry.Featured(0, featA),
                    ArtistEntry.Featured(1, featB),
                ),
                entries,
            )
        }

    @Test
    fun `an artist with no albums and no features shows only the songs section`() =
        runTest {
            val viewModel =
                ArtistViewModel(
                    playback,
                    FakeSongReader(artistSongs = mapOf(ArtistId(2) to listOf(night))),
                    FakeArtistReader(listOf(loner)),
                    FakeAlbumReader(),
                )
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }
            viewModel.open(ArtistId(2))

            assertEquals(
                listOf(ArtistEntry.Header(ArtistSection.SONGS), ArtistEntry.Track(0, night)),
                viewModel.ready().entries,
            )
        }

    @Test
    fun `tapping the 2nd song plays the own songs in the listed order from it`() =
        runTest {
            val viewModel = open()

            viewModel.onSongClick(1)

            assertEquals(
                listOf(request(ownSongs, startIndex = 1, shuffle = false, PlaySource.ARTIST, picked = true)),
                playback.requests,
            )
        }

    @Test
    fun `Shuffle queues the own songs then the featured ones, shuffled, as artist_shuffle`() =
        runTest {
            val viewModel = open()

            viewModel.onShuffle()

            val sent = playback.requests.single()
            assertEquals(
                request(
                    ownSongs + listOf(featA, featB),
                    startIndex = 0,
                    shuffle = true,
                    PlaySource.ARTIST_SHUFFLE,
                    picked = false,
                ),
                sent,
            )
            assertTrue(sent.songs.containsAll(listOf(featA.ref, featB.ref)))
        }

    @Test
    fun `Play plays the own songs in the listed order from the first, not picked`() =
        runTest {
            val viewModel = open()

            viewModel.onPlay()

            assertEquals(
                listOf(request(ownSongs, startIndex = 0, shuffle = false, PlaySource.ARTIST, picked = false)),
                playback.requests,
            )
        }

    @Test
    fun `tapping a featured song plays the featured list from it and never the own songs`() =
        runTest {
            val viewModel = open()

            viewModel.onFeaturedClick(1)

            assertEquals(
                listOf(
                    request(listOf(featA, featB), startIndex = 1, shuffle = false, PlaySource.ARTIST, picked = true),
                ),
                playback.requests,
            )
        }

    @Test
    fun `a song index outside the list plays nothing`() =
        runTest {
            val viewModel = open()

            viewModel.onSongClick(3)
            viewModel.onFeaturedClick(2)

            assertTrue(playback.requests.isEmpty())
        }

    @Test
    fun `an artist that does not exist shows not found`() =
        runTest {
            assertEquals(ArtistUiState.NotFound, open(id = 99).state.value)
        }

    @Test
    fun `Play next and Add to queue send that one song`() =
        runTest {
            val viewModel = open()

            viewModel.onPlayNext(night)
            viewModel.onAddToQueue(featA)

            assertEquals(listOf(listOf(night.ref)), playback.playNextCalls)
            assertEquals(listOf(listOf(featA.ref)), playback.addToQueueCalls)
        }
}
