package dev.tomasflopes.musicplayer.ui.playlists

import dev.tomasflopes.musicplayer.core.model.SongId
import dev.tomasflopes.musicplayer.core.model.log.PlaySource
import dev.tomasflopes.musicplayer.core.model.playlists.AddSongsToPlaylist
import dev.tomasflopes.musicplayer.core.model.playlists.CreatePlaylist
import dev.tomasflopes.musicplayer.core.model.playlists.InMemoryPlaylists
import dev.tomasflopes.musicplayer.core.model.playlists.PlaylistId
import dev.tomasflopes.musicplayer.core.model.playlists.RenamePlaylist
import dev.tomasflopes.musicplayer.core.model.ref
import dev.tomasflopes.musicplayer.core.model.undo.SingleUndoSlot
import dev.tomasflopes.musicplayer.fakes.FakePlaybackController
import dev.tomasflopes.musicplayer.fakes.testSong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
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

@OptIn(ExperimentalCoroutinesApi::class)
class PlaylistViewModelTest {
    private val songs = (1L..3L).map { testSong(it) }
    private val store = InMemoryPlaylists(songs)
    private val clock = Clock.fixed(Instant.ofEpochMilli(1_000), ZoneOffset.UTC)
    private val playback = FakePlaybackController()

    @BeforeEach
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    private val undo = SingleUndoSlot()

    private fun viewModel() = PlaylistViewModel(store, store, RenamePlaylist(store, clock), playback, undo, clock)

    private suspend fun opened(): Pair<PlaylistViewModel, PlaylistId> {
        val created = CreatePlaylist(store, clock)("Gym", listOf(SongId(3), SongId(1))) as CreatePlaylist.Result.Created
        val viewModel = viewModel()
        viewModel.open(created.id)
        return viewModel to created.id
    }

    @Test
    fun `before it is opened the screen is loading`() =
        runTest {
            assertEquals(PlaylistUiState.Loading, viewModel().state.value)
        }

    @Test
    fun `an unknown playlist is not found`() =
        runTest {
            val viewModel = viewModel()
            viewModel.open(PlaylistId(99))

            assertEquals(PlaylistUiState.NotFound, viewModel.state.first { it != PlaylistUiState.Loading })
        }

    @Test
    fun `the screen shows the name and the songs in playlist order`() =
        runTest {
            val (viewModel, _) = opened()

            val ready = viewModel.state.first { it is PlaylistUiState.Ready } as PlaylistUiState.Ready

            assertEquals("Gym", ready.name)
            assertEquals(listOf(songs[2], songs[0]), ready.songs)
        }

    @Test
    fun `tapping a song plays the playlist in order from it`() =
        runTest {
            val (viewModel, _) = opened()
            viewModel.state.first { it is PlaylistUiState.Ready }

            viewModel.onSongClick(1)

            val request = playback.requests.single()
            assertEquals(listOf(songs[2].ref, songs[0].ref), request.songs)
            assertEquals(1, request.startIndex)
            assertEquals(PlaySource.PLAYLIST, request.source)
            assertEquals(false, request.shuffle)
            assertEquals(true, request.picked)
        }

    private suspend fun PlaylistViewModel.ready() = state.first { it is PlaylistUiState.Ready } as PlaylistUiState.Ready

    @Test
    fun `play starts the playlist in order and logs its id`() =
        runTest {
            val (viewModel, id) = opened()
            viewModel.ready()

            viewModel.onPlay()

            val request = playback.requests.single()
            assertEquals(false, request.shuffle)
            assertEquals(false, request.picked)
            assertEquals(0, request.startIndex)
            assertEquals(PlaySource.PLAYLIST, request.source)
            assertEquals(id.value, request.playlistId)
        }

    @Test
    fun `shuffle asks for the shuffle strategy and logs the playlist id`() =
        runTest {
            val (viewModel, id) = opened()
            viewModel.ready()

            viewModel.onShuffle()

            val request = playback.requests.single()
            assertEquals(true, request.shuffle)
            assertEquals(false, request.picked)
            assertEquals(id.value, request.playlistId)
        }

    @Test
    fun `tapping a song logs the playlist id too`() =
        runTest {
            val (viewModel, id) = opened()
            viewModel.ready()

            viewModel.onSongClick(0)

            assertEquals(id.value, playback.requests.single().playlistId)
        }

    @Test
    fun `rename changes the title and a blank name is refused`() =
        runTest {
            val (viewModel, _) = opened()
            viewModel.ready()

            assertEquals(true, viewModel.rename("  Run  "))
            assertEquals("Run", viewModel.ready().name)
            assertEquals(false, viewModel.rename("   "))
            assertEquals("Run", viewModel.ready().name)
        }

    @Test
    fun `delete makes the playlist not found`() =
        runTest {
            val (viewModel, _) = opened()
            viewModel.ready()

            viewModel.delete()

            assertEquals(PlaylistUiState.NotFound, viewModel.state.first { it == PlaylistUiState.NotFound })
        }

    @Test
    fun `remove takes one song out and keeps the rest in order`() =
        runTest {
            val (viewModel, _) = opened()
            viewModel.ready()

            viewModel.remove(songs[2].id)

            assertEquals(listOf(songs[0]), viewModel.ready().songs)
        }

    @Test
    fun `remove offers an undo that puts the song back at its place`() =
        runTest {
            val (viewModel, _) = opened()
            viewModel.ready()

            viewModel.remove(songs[2].id)
            viewModel.ready()
            val offer = undo.pending.value

            assertEquals("Removed \"${songs[2].title}\" from Gym", offer?.message)
            undo.undo(offer!!.id)

            val restored = viewModel.state.first { (it as? PlaylistUiState.Ready)?.songs?.size == 2 } as PlaylistUiState.Ready
            assertEquals(listOf(songs[2], songs[0]), restored.songs)
        }

    @Test
    fun `a second removal replaces the pending undo, and only the last one can be undone`() =
        runTest {
            val (viewModel, _) = opened()
            viewModel.ready()

            viewModel.remove(songs[2].id)
            val first = undo.pending.value!!
            viewModel.remove(songs[0].id)
            val second = undo.pending.value!!

            assertEquals(false, undo.undo(first.id))
            assertEquals(second, undo.pending.value)
        }

    @Test
    fun `removing a song that is not there offers no undo`() =
        runTest {
            val (viewModel, _) = opened()
            viewModel.ready()

            viewModel.remove(songs[1].id)

            assertEquals(null, undo.pending.value)
        }

    @Test
    fun `move puts a song at its new place`() =
        runTest {
            val (viewModel, id) = opened()
            addSecond(id)
            viewModel.ready()

            viewModel.move(from = 0, to = 2)

            assertEquals(listOf(songs[0], songs[1], songs[2]), viewModel.ready().songs)
        }

    @Test
    fun `a move outside the list changes nothing`() =
        runTest {
            val (viewModel, _) = opened()
            viewModel.ready()

            viewModel.move(from = 0, to = 5)
            viewModel.move(from = -1, to = 0)

            assertEquals(listOf(songs[2], songs[0]), viewModel.ready().songs)
        }

    private suspend fun addSecond(id: PlaylistId) {
        AddSongsToPlaylist(store, clock)(id, listOf(SongId(2)))
    }
}
