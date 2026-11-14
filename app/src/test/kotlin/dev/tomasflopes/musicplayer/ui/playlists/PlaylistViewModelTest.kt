package dev.tomasflopes.musicplayer.ui.playlists

import dev.tomasflopes.musicplayer.core.model.SongId
import dev.tomasflopes.musicplayer.core.model.log.PlaySource
import dev.tomasflopes.musicplayer.core.model.playlists.CreatePlaylist
import dev.tomasflopes.musicplayer.core.model.playlists.InMemoryPlaylists
import dev.tomasflopes.musicplayer.core.model.playlists.PlaylistId
import dev.tomasflopes.musicplayer.core.model.ref
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

    private suspend fun opened(): Pair<PlaylistViewModel, PlaylistId> {
        val created = CreatePlaylist(store, clock)("Gym", listOf(SongId(3), SongId(1))) as CreatePlaylist.Result.Created
        val viewModel = PlaylistViewModel(store, playback)
        viewModel.open(created.id)
        return viewModel to created.id
    }

    @Test
    fun `before it is opened the screen is loading`() =
        runTest {
            assertEquals(PlaylistUiState.Loading, PlaylistViewModel(store, playback).state.value)
        }

    @Test
    fun `an unknown playlist is not found`() =
        runTest {
            val viewModel = PlaylistViewModel(store, playback)
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
}
