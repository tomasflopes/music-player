package dev.tomasflopes.musicplayer.ui.search

import dev.tomasflopes.musicplayer.core.model.AlbumId
import dev.tomasflopes.musicplayer.core.model.ArtistId
import dev.tomasflopes.musicplayer.core.model.SongId
import dev.tomasflopes.musicplayer.core.model.log.PlaySource
import dev.tomasflopes.musicplayer.core.model.log.SearchEvent
import dev.tomasflopes.musicplayer.core.model.log.SearchItemType
import dev.tomasflopes.musicplayer.core.model.ref
import dev.tomasflopes.musicplayer.fakes.FakeAlbumReader
import dev.tomasflopes.musicplayer.fakes.FakeArtistReader
import dev.tomasflopes.musicplayer.fakes.FakePlaybackController
import dev.tomasflopes.musicplayer.fakes.FakeSearchHistoryWriter
import dev.tomasflopes.musicplayer.fakes.FakeSongFeatureReader
import dev.tomasflopes.musicplayer.fakes.FakeSongReader
import dev.tomasflopes.musicplayer.fakes.testAlbum
import dev.tomasflopes.musicplayer.fakes.testArtist
import dev.tomasflopes.musicplayer.fakes.testSong
import dev.tomasflopes.musicplayer.playback.PlayRequest
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {
    private lateinit var dispatcher: CoroutineDispatcher

    private val ed = testArtist(1, "Ed")
    private val eddie = testArtist(2, "Eddie Kane")
    private val kendrick = testArtist(3, "Kendrick Lamar")
    private val jayZ = testArtist(4, "JAY-Z")
    private val sample = testArtist(5, "Sample B")

    private val education = testAlbum(10, "Education", eddie.name, coverSongId = 104)

    private val morning = testSong(100, albumId = null, title = "Morning", artistId = 1, artistName = "Ed")
    private val night =
        testSong(101, albumId = null, title = "Night feat. Ed", artistId = 3, artistName = "Kendrick Lamar")
    private val faded = testSong(102, albumId = null, title = "Faded", artistId = 5, artistName = "Sample B")
    private val ended = testSong(103, albumId = null, title = "Ended", artistId = 5, artistName = "Sample B")
    private val bigEd =
        testSong(
            104,
            albumId = 10,
            title = "Big Ed",
            artistId = 2,
            artistName = "Eddie Kane",
            albumTitle = "Education",
        )
    private val empire = testSong(105, albumId = null, title = "Empire", artistId = 4, artistName = "JAY-Z")

    private val playback = FakePlaybackController()
    private val history = FakeSearchHistoryWriter()
    private val songReader =
        FakeSongReader(
            songs = listOf(morning, night, faded, ended, bigEd, empire),
            artistSongs = mapOf(ArtistId(1) to listOf(morning), ArtistId(4) to listOf(empire)),
            albumSongs = mapOf(AlbumId(10) to listOf(bigEd)),
        )
    private val artistReader = FakeArtistReader(listOf(ed, eddie, kendrick, jayZ, sample))
    private val albumReader = FakeAlbumReader(listOf(education))
    private val featureReader = FakeSongFeatureReader(mapOf(night.id to listOf("Ed")))
    private val clock = Clock.fixed(Instant.ofEpochMilli(NOW), ZoneOffset.UTC)

    @BeforeEach
    fun setUp() {
        dispatcher = UnconfinedTestDispatcher()
        Dispatchers.setMain(dispatcher)
    }

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    /** A ViewModel whose state is being collected, as it is while the Search tab is on screen. */
    private fun TestScope.viewModel(): SearchViewModel {
        val viewModel =
            SearchViewModel(
                playback,
                songReader,
                artistReader,
                albumReader,
                featureReader,
                history,
                clock,
                CoreSearchEngineFactory(dispatcher = dispatcher),
            )
        backgroundScope.launch(dispatcher) { viewModel.state.collect {} }
        return viewModel
    }

    private fun TestScope.typed(
        viewModel: SearchViewModel,
        text: String,
    ): SearchUiState {
        viewModel.onQueryChange(text)
        advanceUntilIdle()
        return viewModel.state.value
    }

    private fun SearchUiState.results(): SearchSections =
        assertInstanceOf(SearchUiState.Results::class.java, this).sections

    private fun SearchUiState.seeAll(): SearchUiState.SeeAll = assertInstanceOf(SearchUiState.SeeAll::class.java, this)

    @Test
    fun `a blank query shows the idle state`() =
        runTest {
            val viewModel = viewModel()

            assertEquals(SearchUiState.Idle, viewModel.state.value)
            assertEquals(SearchUiState.Idle, typed(viewModel, "   "))
        }

    @Test
    fun `a query typed before the index is built shows indexing, then the results`() =
        runTest {
            val viewModel = viewModel()
            viewModel.onQueryChange("ed")

            assertEquals(SearchUiState.Indexing, viewModel.state.value)

            advanceUntilIdle()

            assertInstanceOf(SearchUiState.Results::class.java, viewModel.state.value)
        }

    @Test
    fun `ed puts the artist Ed first`() =
        runTest {
            val viewModel = viewModel()

            val top = typed(viewModel, "ed").results().top

            assertEquals(ArtistItem(ed), top)
        }

    @Test
    fun `jayz finds JAY-Z and shows it as tagged`() =
        runTest {
            val viewModel = viewModel()

            val top = typed(viewModel, "jayz").results().top

            assertEquals("JAY-Z", assertInstanceOf(ArtistItem::class.java, top).artist.name)
        }

    @Test
    fun `mid-word matches are left out of the section and found last under See all`() =
        runTest {
            val viewModel = viewModel()

            val sections = typed(viewModel, "ed").results()
            val sectionTitles = sections.songs.map { it.song.title }
            viewModel.onSeeAll(SearchItemType.SONG)
            advanceUntilIdle()
            val all = viewModel.state.value.seeAll()
            val allTitles = all.items.map { (it as SongItem).song.title }

            assertTrue("Faded" !in sectionTitles && "Ended" !in sectionTitles)
            assertTrue(sections.moreSongs, "See all must be offered for the hidden mid-word matches")
            assertEquals(SearchItemType.SONG, all.type)
            assertEquals(setOf("Faded", "Ended"), allTitles.takeLast(2).toSet())
            assertTrue(allTitles.indexOf("Big Ed") < allTitles.indexOf("Faded"))
        }

    @Test
    fun `an album result carries a song to take its cover from`() =
        runTest {
            val viewModel = viewModel()

            val top = typed(viewModel, "education").results().top

            assertEquals(AlbumItem(education, bigEd), top)
        }

    @Test
    fun `closing See all returns to the results, and clearing the query forgets it`() =
        runTest {
            val viewModel = viewModel()
            typed(viewModel, "ed")
            viewModel.onSeeAll(SearchItemType.SONG)
            advanceUntilIdle()
            viewModel.onCloseSeeAll()
            advanceUntilIdle()
            assertInstanceOf(SearchUiState.Results::class.java, viewModel.state.value)

            viewModel.onSeeAll(SearchItemType.SONG)
            advanceUntilIdle()
            typed(viewModel, "")

            assertEquals(SearchUiState.Idle, viewModel.state.value)
            assertInstanceOf(SearchUiState.Results::class.java, typed(viewModel, "ed"))
        }

    @Test
    fun `a query with no match shows no results`() =
        runTest {
            val viewModel = viewModel()

            assertEquals(SearchUiState.NoResults("zzzz"), typed(viewModel, "zzzz"))
        }

    @Test
    fun `choosing an artist logs the search and shuffles that artist`() =
        runTest {
            val viewModel = viewModel()
            val top = typed(viewModel, "ed").results().top

            viewModel.onResultClick(top)
            advanceUntilIdle()

            assertEquals(listOf(SearchEvent("ed", SearchItemType.ARTIST, 1, NOW)), history.events)
            assertEquals(
                listOf(PlayRequest(listOf(morning.ref), 0, shuffle = true, PlaySource.ARTIST_SHUFFLE, picked = false)),
                playback.requests,
            )
        }

    @Test
    fun `the logged query is the trimmed text as typed`() =
        runTest {
            val viewModel = viewModel()
            val top = typed(viewModel, "  JayZ ").results().top

            viewModel.onPlayTop(top)
            advanceUntilIdle()

            assertEquals(listOf(SearchEvent("JayZ", SearchItemType.ARTIST, 4, NOW)), history.events)
            assertEquals(
                listOf(PlayRequest(listOf(empire.ref), 0, shuffle = false, PlaySource.ARTIST, picked = false)),
                playback.requests,
            )
        }

    @Test
    fun `playing the top album plays it in order, and shuffle shuffles it`() =
        runTest {
            val viewModel = viewModel()
            val top = typed(viewModel, "education").results().top

            viewModel.onPlayTop(top)
            viewModel.onShuffleTop(top)
            advanceUntilIdle()

            assertEquals(
                listOf(
                    PlayRequest(listOf(bigEd.ref), 0, shuffle = false, PlaySource.ALBUM, picked = false),
                    PlayRequest(listOf(bigEd.ref), 0, shuffle = true, PlaySource.ALBUM, picked = false),
                ),
                playback.requests,
            )
            assertEquals(2, history.events.size)
            assertTrue(history.events.all { it.chosenType == SearchItemType.ALBUM && it.chosenId == 10L })
        }

    @Test
    fun `choosing a song logs the search and plays the shown songs from it as a search`() =
        runTest {
            val viewModel = viewModel()
            val sections = typed(viewModel, "ed").results()
            val shown = sections.songs.map { it.song }

            viewModel.onSongClick(1)
            advanceUntilIdle()

            assertEquals(
                listOf(PlayRequest(shown.map { it.ref }, 1, shuffle = false, PlaySource.SEARCH, picked = true)),
                playback.requests,
            )
            assertEquals(
                listOf(SearchEvent("ed", SearchItemType.SONG, shown[1].id.value, NOW)),
                history.events,
            )
        }

    @Test
    fun `a song chosen under See all plays the whole See all list from it`() =
        runTest {
            val viewModel = viewModel()
            typed(viewModel, "ed")
            viewModel.onSeeAll(SearchItemType.SONG)
            advanceUntilIdle()
            val seeAll = viewModel.state.value.seeAll()
            val shown = seeAll.items.map { (it as SongItem).song }
            val last = shown.lastIndex

            viewModel.onSongClick(last)
            advanceUntilIdle()

            assertEquals(
                listOf(PlayRequest(shown.map { it.ref }, last, shuffle = false, PlaySource.SEARCH, picked = true)),
                playback.requests,
            )
            assertEquals(shown[last].id, SongId(history.events.single().chosenId))
        }

    @Test
    fun `a song index outside the shown list does nothing`() =
        runTest {
            val viewModel = viewModel()
            typed(viewModel, "ed")

            viewModel.onSongClick(99)
            advanceUntilIdle()

            assertTrue(playback.requests.isEmpty() && history.events.isEmpty())
        }

    @Test
    fun `a library change rebuilds the index`() =
        runTest {
            val viewModel = viewModel()
            val beforeSections = typed(viewModel, "ed").results()
            val before = beforeSections.artists.map { it.artist.name }

            artistReader.library.value = artistReader.library.value + testArtist(6, "Edgar")
            advanceUntilIdle()
            val afterSections = viewModel.state.value.results()
            val after = afterSections.artists.map { it.artist.name }

            assertEquals(listOf("Ed", "Eddie Kane"), before)
            assertEquals(listOf("Ed", "Eddie Kane", "Edgar"), after)
        }

    private companion object {
        const val NOW = 1_700_000_000_000L
    }
}
