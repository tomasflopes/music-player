package dev.tomasflopes.musicplayer.core.model.playlists

import dev.tomasflopes.musicplayer.core.model.SongId
import dev.tomasflopes.musicplayer.core.model.log.PlaySource
import dev.tomasflopes.musicplayer.core.model.song
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class SaveAsPlaylistTest {
    private val clock = Clock.fixed(Instant.ofEpochMilli(5_000), ZoneOffset.UTC)
    private val library = (1L..600L).map { song("s$it", id = it) }
    private val playlists = InMemoryPlaylists(library)
    private val save = SaveAsPlaylist(CreatePlaylist(playlists, clock))

    private fun ids(range: LongRange) = range.map(::SongId)

    @Test
    fun `a list becomes a playlist with its songs in the same order`() =
        runTest {
            val result = save("Road trip", listOf(SongId(3), SongId(1), SongId(2)), listOf(PlaySource.ALBUM))

            val saved = result as SaveAsPlaylist.Result.Saved
            assertEquals(3, saved.added)
            assertEquals("Road trip", playlists.nameOf(saved.id))
            assertEquals(listOf(SongId(3), SongId(1), SongId(2)), playlists.storedSongs(saved.id))
        }

    @Test
    fun `a song that is in the list twice goes in once`() =
        runTest {
            val saved = save("Mix", listOf(SongId(1), SongId(2), SongId(1)), listOf(PlaySource.SONGS))

            assertEquals(listOf(SongId(1), SongId(2)), playlists.storedSongs((saved as SaveAsPlaylist.Result.Saved).id))
        }

    @Test
    fun `exactly 500 songs are saved`() =
        runTest {
            val saved = save("Big", ids(1L..500L), listOf(PlaySource.TOP100))

            assertEquals(500, (saved as SaveAsPlaylist.Result.Saved).added)
        }

    @Test
    fun `more than 500 songs are refused and nothing is created`() =
        runTest {
            val result = save("Too big", ids(1L..501L), listOf(PlaySource.SONGS))

            assertEquals(SaveAsPlaylist.Result.TooManySongs(SaveAsPlaylist.MAX_SONGS), result)
            assertTrue(playlists.observePlaylists().first().isEmpty())
        }

    @Test
    fun `a Shuffle all list is never saved`() =
        runTest {
            val result = save("Everything", listOf(SongId(1)), listOf(PlaySource.SHUFFLE_ALL))

            assertEquals(SaveAsPlaylist.Result.ShuffleAll, result)
            assertTrue(playlists.observePlaylists().first().isEmpty())
        }

    @Test
    fun `a queue that began as Shuffle all is refused even with songs queued by hand`() =
        runTest {
            val result = save("Q", listOf(SongId(1), SongId(2)), listOf(PlaySource.QUEUE, PlaySource.SHUFFLE_ALL))

            assertEquals(SaveAsPlaylist.Result.ShuffleAll, result)
        }

    @Test
    fun `a queue of a shuffled album, with songs queued by hand, can be saved`() =
        runTest {
            val result = save("Q", listOf(SongId(1), SongId(2)), listOf(PlaySource.QUEUE, PlaySource.ALBUM))

            assertEquals(2, (result as SaveAsPlaylist.Result.Saved).added)
        }

    @Test
    fun `an empty list and a blank name are refused`() =
        runTest {
            assertEquals(SaveAsPlaylist.Result.Empty, save("Nothing", emptyList(), listOf(PlaySource.ALBUM)))
            assertEquals(SaveAsPlaylist.Result.InvalidName, save("   ", listOf(SongId(1)), listOf(PlaySource.ALBUM)))
            assertTrue(playlists.observePlaylists().first().isEmpty())
        }
}
