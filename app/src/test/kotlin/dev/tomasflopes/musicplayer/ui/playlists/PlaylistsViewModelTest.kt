package dev.tomasflopes.musicplayer.ui.playlists

import dev.tomasflopes.musicplayer.core.model.SongId
import dev.tomasflopes.musicplayer.core.model.playlists.AddSongsToPlaylist
import dev.tomasflopes.musicplayer.core.model.playlists.CreatePlaylist
import dev.tomasflopes.musicplayer.core.model.playlists.InMemoryPlaylists
import dev.tomasflopes.musicplayer.fakes.FakeSongReader
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
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class PlaylistsViewModelTest {
    private val songs = (1L..3L).map { testSong(it) }
    private val store = InMemoryPlaylists(songs)
    private val reader = FakeSongReader(songs = songs)
    private val clock = Clock.fixed(Instant.ofEpochMilli(1_000), ZoneOffset.UTC)

    @BeforeEach
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = PlaylistsViewModel(store, reader, CreatePlaylist(store, clock))

    @Test
    fun `an empty library of playlists shows no cards`() =
        runTest {
            assertEquals(emptyList<PlaylistItem>(), viewModel().items.value)
        }

    @Test
    fun `a card has the playlist and the cover of its first song`() =
        runTest {
            val viewModel = viewModel()
            val created =
                CreatePlaylist(
                    store,
                    clock,
                )("Gym", listOf(SongId(2), SongId(1))) as CreatePlaylist.Result.Created

            val item = viewModel.items.first { it.isNotEmpty() }.single()

            assertEquals(created.id, item.playlist.id)
            assertEquals(2, item.playlist.songCount)
            assertEquals(songs[1], item.cover)
        }

    @Test
    fun `an empty playlist has no cover`() =
        runTest {
            val viewModel = viewModel()
            CreatePlaylist(store, clock)("Empty")

            assertNull(
                viewModel.items
                    .first { it.isNotEmpty() }
                    .single()
                    .cover,
            )
        }

    @Test
    fun `a playlist's cover follows its songs`() =
        runTest {
            val viewModel = viewModel()
            val created = CreatePlaylist(store, clock)("Gym", listOf(SongId(1))) as CreatePlaylist.Result.Created

            AddSongsToPlaylist(store, clock)(created.id, listOf(SongId(3)))

            assertEquals(
                songs[0],
                viewModel.items
                    .first { it.isNotEmpty() }
                    .single()
                    .cover,
            )
        }

    @Test
    fun `creating returns the new id and a blank name returns nothing`() =
        runTest {
            val viewModel = viewModel()

            assertNotNull(viewModel.create(" Run "))
            assertNull(viewModel.create("  "))
            assertEquals(1, store.observePlaylists().first().size)
        }
}
