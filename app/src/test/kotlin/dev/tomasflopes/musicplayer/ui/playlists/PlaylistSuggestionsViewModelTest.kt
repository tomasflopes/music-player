package dev.tomasflopes.musicplayer.ui.playlists

import dev.tomasflopes.musicplayer.core.model.Song
import dev.tomasflopes.musicplayer.core.model.SongArtists
import dev.tomasflopes.musicplayer.core.model.SongId
import dev.tomasflopes.musicplayer.core.model.playlists.AddSongsToPlaylist
import dev.tomasflopes.musicplayer.core.model.playlists.InMemoryDismissals
import dev.tomasflopes.musicplayer.core.model.playlists.InMemoryPlaylists
import dev.tomasflopes.musicplayer.core.model.playlists.PlaylistId
import dev.tomasflopes.musicplayer.core.model.ports.SongArtistSource
import dev.tomasflopes.musicplayer.core.taste.score.ArtistScore
import dev.tomasflopes.musicplayer.core.taste.score.ScoreBoard
import dev.tomasflopes.musicplayer.core.taste.score.SongScore
import dev.tomasflopes.musicplayer.core.taste.suggest.AlbumLinkFilter
import dev.tomasflopes.musicplayer.core.taste.suggest.ArtistLinkFilter
import dev.tomasflopes.musicplayer.core.taste.suggest.DismissedNeighbourFilter
import dev.tomasflopes.musicplayer.core.taste.suggest.PlaylistSuggestions
import dev.tomasflopes.musicplayer.core.taste.suggest.SuggestionPlace
import dev.tomasflopes.musicplayer.core.taste.suggest.SuggestionRanker
import dev.tomasflopes.musicplayer.core.taste.suggest.TasteScoreFilter
import dev.tomasflopes.musicplayer.fakes.FakeSongReader
import dev.tomasflopes.musicplayer.fakes.testSong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.coroutines.EmptyCoroutineContext

@OptIn(ExperimentalCoroutinesApi::class)
class PlaylistSuggestionsViewModelTest {
    /** Songs 1-6 are by artist 10, song 7 by artist 20; all on their own albums. */
    private val songs =
        (1L..6L).map { testSong(it, artistId = 10, albumId = it) } + testSong(7, artistId = 20, albumId = 7)
    private val playlists = InMemoryPlaylists(songs)
    private val dismissals = InMemoryDismissals()
    private val clock = Clock.fixed(Instant.ofEpochMilli(1_000), ZoneOffset.UTC)

    @BeforeEach
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    private val board =
        object : ScoreBoard {
            override fun observeRanked(): Flow<List<SongScore>> = flowOf(emptyList())

            override fun observeArtistRanked(): Flow<List<ArtistScore>> = flowOf(emptyList())
        }

    private val artists =
        SongArtistSource { ids ->
            ids.associateWith { id -> SongArtists(songs.first { it.id == id }.artistId) }
        }

    private fun viewModel() =
        PlaylistSuggestionsViewModel(
            PlaylistSuggestions(
                playlists = playlists,
                dismissals = dismissals,
                library = FakeSongReader(songs = songs),
                artists = artists,
                board = board,
                ranker =
                    SuggestionRanker(
                        setOf(ArtistLinkFilter(), AlbumLinkFilter(), TasteScoreFilter(), DismissedNeighbourFilter()),
                    ),
                computeOn = EmptyCoroutineContext,
            ),
            AddSongsToPlaylist(playlists, clock),
            dismissals,
            clock,
        )

    private suspend fun playlistWith(vararg ids: Long): PlaylistId {
        val id = playlists.create("Gym", 1)
        playlists.addSongs(id, ids.map(::SongId), 1)
        return id
    }

    /** Keeps the view model's list alive, like the screen does, so it follows every change. */
    private fun TestScope.watch(viewModel: PlaylistSuggestionsViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.songs.collect {} }
    }

    private fun PlaylistSuggestionsViewModel.shown(): List<Long> = songs.value.map { it.id.value }

    @Test
    fun `before it is opened nothing is suggested`() =
        runTest {
            val viewModel = viewModel()
            watch(viewModel)

            assertEquals(emptyList<Song>(), viewModel.songs.value)
        }

    @Test
    fun `the page suggests songs that are not in the playlist`() =
        runTest {
            val id = playlistWith(1, 2)
            val viewModel = viewModel()
            watch(viewModel)

            viewModel.open(id, SuggestionPlace.PLAYLIST_PAGE)

            assertEquals(listOf(3L, 4L, 5L, 6L), viewModel.shown())
        }

    @Test
    fun `plus adds the song to the playlist and the list refreshes at once`() =
        runTest {
            val id = playlistWith(1, 2)
            val viewModel = viewModel()
            watch(viewModel)
            viewModel.open(id, SuggestionPlace.PLAYLIST_PAGE)

            viewModel.onAdd(songs[2])

            assertEquals(listOf(SongId(1), SongId(2), SongId(3)), playlists.storedSongs(id))
            assertEquals(listOf(4L, 5L, 6L), viewModel.shown())
        }

    @Test
    fun `plus goes through the fast add path, so the playlist counts as used`() =
        runTest {
            val id = playlistWith(1, 2)
            val viewModel = viewModel()
            watch(viewModel)
            viewModel.open(id, SuggestionPlace.PLAYLIST_PAGE)

            viewModel.onAdd(songs[3])

            assertEquals(1_000L, playlists.lastUsedOf(id))
        }

    @Test
    fun `cross removes the song from the list and does not add it`() =
        runTest {
            val id = playlistWith(1, 2)
            val viewModel = viewModel()
            watch(viewModel)
            viewModel.open(id, SuggestionPlace.PLAYLIST_PAGE)

            viewModel.onDismiss(songs[2])

            assertEquals(listOf(4L, 5L, 6L), viewModel.shown())
            assertEquals(listOf(SongId(1), SongId(2)), playlists.storedSongs(id))
        }

    @Test
    fun `a dismissed song comes back in another playlist`() =
        runTest {
            val gym = playlistWith(1, 2)
            val calm = playlistWith(1, 2)
            val viewModel = viewModel()
            watch(viewModel)
            viewModel.open(gym, SuggestionPlace.PLAYLIST_PAGE)
            viewModel.onDismiss(songs[2])

            viewModel.open(calm, SuggestionPlace.PLAYLIST_PAGE)

            assertEquals(listOf(3L, 4L, 5L, 6L), viewModel.shown())
        }

    @Test
    fun `a dismissed song does not come back when the page is opened again`() =
        runTest {
            val id = playlistWith(1, 2)
            val first = viewModel()
            first.open(id, SuggestionPlace.PLAYLIST_PAGE)
            first.onDismiss(songs[2])

            val second = viewModel()
            watch(second)
            second.open(id, SuggestionPlace.PLAYLIST_PAGE)

            assertEquals(listOf(4L, 5L, 6L), second.shown())
        }

    @Test
    fun `the picker of a new playlist waits for two songs`() =
        runTest {
            val id = playlistWith(1)
            val viewModel = viewModel()
            watch(viewModel)
            viewModel.open(id, SuggestionPlace.NEW_PLAYLIST_PICKER)
            assertEquals(emptyList<Long>(), viewModel.shown())

            viewModel.onAdd(songs[1])

            assertEquals(listOf(3L, 4L, 5L, 6L), viewModel.shown())
        }

    @Test
    fun `plus and cross do nothing before a playlist is open`() =
        runTest {
            val viewModel = viewModel()

            viewModel.onAdd(songs[0])
            viewModel.onDismiss(songs[0])

            assertEquals(emptyList<Long>(), viewModel.shown())
        }
}
