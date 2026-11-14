package dev.tomasflopes.musicplayer.ui.playlists

import dev.tomasflopes.musicplayer.core.model.SongId
import dev.tomasflopes.musicplayer.core.model.log.PlaySource
import dev.tomasflopes.musicplayer.core.model.playlists.CreatePlaylist
import dev.tomasflopes.musicplayer.core.model.playlists.InMemoryPlaylists
import dev.tomasflopes.musicplayer.core.model.playlists.SaveAsPlaylist
import dev.tomasflopes.musicplayer.fakes.FakePlaybackController
import dev.tomasflopes.musicplayer.fakes.testSong
import dev.tomasflopes.musicplayer.playback.QueueContent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class SaveAsPlaylistViewModelTest {
    private val store = InMemoryPlaylists((1L..4L).map { testSong(it) })
    private val playback = FakePlaybackController()
    private val viewModel =
        SaveAsPlaylistViewModel(
            SaveAsPlaylist(CreatePlaylist(store, Clock.fixed(Instant.ofEpochMilli(1_000), ZoneOffset.UTC))),
            playback,
        )

    private suspend fun names() = store.observePlaylists().first().map { it.name }

    @Test
    fun `saving a list says how many songs went into which playlist`() =
        runTest {
            val message = viewModel.saveList("Chill", listOf(SongId(1), SongId(2)), PlaySource.ALBUM)

            assertEquals("Saved 2 songs as Chill", message)
            assertEquals(listOf("Chill"), names())
        }

    @Test
    fun `one song reads in the singular`() =
        runTest {
            assertEquals("Saved 1 song as Solo", viewModel.saveList("Solo", listOf(SongId(1)), PlaySource.SONGS))
        }

    @Test
    fun `saving the queue takes the queue as it is now`() =
        runTest {
            playback.queue = QueueContent(listOf(SongId(3), SongId(1)), setOf(PlaySource.ARTIST))

            assertEquals("Saved 2 songs as Queue", viewModel.saveQueue("Queue"))
            assertEquals(listOf("Queue"), names())
        }

    @Test
    fun `a Shuffle all queue is refused with a reason and nothing is created`() =
        runTest {
            playback.queue = QueueContent(listOf(SongId(3)), setOf(PlaySource.SHUFFLE_ALL))

            assertEquals("Shuffle all can't be saved as a playlist", viewModel.saveQueue("Queue"))
            assertEquals(emptyList<String>(), names())
        }

    @Test
    fun `too many songs, an empty list and a bad name each get their own message`() =
        runTest {
            val many = (1L..501L).map(::SongId)

            assertEquals("A saved playlist holds up to 500 songs", viewModel.saveList("Big", many, PlaySource.SONGS))
            assertEquals("Nothing to save", viewModel.saveList("None", emptyList(), PlaySource.SONGS))
            assertEquals("That is not a valid name", viewModel.saveList("  ", listOf(SongId(1)), PlaySource.SONGS))
        }
}
