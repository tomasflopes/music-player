package dev.tomasflopes.musicplayer.core.model.playlists

import dev.tomasflopes.musicplayer.core.model.AlbumId
import dev.tomasflopes.musicplayer.core.model.ArtistId
import dev.tomasflopes.musicplayer.core.model.Song
import dev.tomasflopes.musicplayer.core.model.SongId
import dev.tomasflopes.musicplayer.core.model.SongRef
import dev.tomasflopes.musicplayer.core.model.ports.SongReader
import dev.tomasflopes.musicplayer.core.model.song
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class PlaylistUseCasesTest {
    private val clock = Clock.fixed(Instant.ofEpochMilli(5_000), ZoneOffset.UTC)
    private val library = (1L..6L).map { song("s$it", id = it) }
    private val playlists = InMemoryPlaylists(library)
    private val add = AddSongsToPlaylist(playlists, clock)

    private fun ids(vararg values: Long) = values.map(::SongId)

    private suspend fun newPlaylist(vararg first: Long) =
        (CreatePlaylist(playlists, clock)("Gym", ids(*first)) as CreatePlaylist.Result.Created).id

    @Test
    fun `creating a playlist trims the name and puts the first songs in, in order`() =
        runTest {
            val result = CreatePlaylist(playlists, clock)("  Gym ", ids(3, 1))

            val created = result as CreatePlaylist.Result.Created
            assertEquals("Gym", playlists.nameOf(created.id))
            assertEquals(ids(3, 1), playlists.storedSongs(created.id))
            assertEquals(2, created.added)
            assertEquals(5_000L, playlists.lastUsedOf(created.id))
        }

    @Test
    fun `a blank name creates nothing`() =
        runTest {
            val result = CreatePlaylist(playlists, clock)("   ", ids(1))

            assertEquals(CreatePlaylist.Result.InvalidName, result)
            assertTrue(playlists.observePlaylistsNow().isEmpty())
        }

    @Test
    fun `a playlist can start empty`() =
        runTest {
            val created = CreatePlaylist(playlists, clock)("Empty") as CreatePlaylist.Result.Created

            assertEquals(0, created.added)
            assertEquals(emptyList<SongId>(), playlists.storedSongs(created.id))
        }

    @Test
    fun `adding puts songs after the ones already there and counts them`() =
        runTest {
            val id = newPlaylist(1)

            val result = add(id, ids(4, 2))

            assertEquals(AddResult(added = 2, alreadyThere = 0), result)
            assertEquals(ids(1, 4, 2), playlists.storedSongs(id))
        }

    @Test
    fun `a song already in the playlist is skipped, not doubled, and the rest still go in`() =
        runTest {
            val id = newPlaylist(1, 2)

            val result = add(id, ids(2, 5, 1, 6))

            assertEquals(AddResult(added = 2, alreadyThere = 2), result)
            assertEquals(ids(1, 2, 5, 6), playlists.storedSongs(id))
        }

    @Test
    fun `the same song twice in one request is added once`() =
        runTest {
            val id = newPlaylist()

            val result = add(id, ids(3, 3, 3))

            assertEquals(AddResult(added = 1, alreadyThere = 0), result)
            assertEquals(ids(3), playlists.storedSongs(id))
        }

    @Test
    fun `adding a song that is already there still counts as using the playlist`() =
        runTest {
            val id = newPlaylist(1)
            val later = AddSongsToPlaylist(playlists, Clock.fixed(Instant.ofEpochMilli(9_000), ZoneOffset.UTC))

            later(id, ids(1))

            assertEquals(9_000L, playlists.lastUsedOf(id))
        }

    @Test
    fun `adding nothing does not touch the playlist`() =
        runTest {
            val id = newPlaylist(1)
            val later = AddSongsToPlaylist(playlists, Clock.fixed(Instant.ofEpochMilli(9_000), ZoneOffset.UTC))

            val result = later(id, emptyList())

            assertEquals(AddResult(0, 0), result)
            assertEquals(5_000L, playlists.lastUsedOf(id))
        }

    @Test
    fun `renaming trims and refuses a blank name`() =
        runTest {
            val id = newPlaylist()
            val rename = RenamePlaylist(playlists, clock)

            assertTrue(rename(id, " Run "))
            assertEquals("Run", playlists.nameOf(id))
            assertEquals(false, rename(id, " "))
            assertEquals("Run", playlists.nameOf(id))
        }

    @Test
    fun `a whole album is added in disc and track order whatever order the reader gives`() =
        runTest {
            val t2 = song("t2", track = 2, id = 12)
            val t1 = song("t1", track = 1, id = 11)
            val d2 = song("d2", disc = 2, track = 1, id = 13)
            val reader = AlbumReader(AlbumId(1), listOf(d2, t2, t1))
            val id = newPlaylist()

            val result = AddAlbumToPlaylist(reader, add)(id, AlbumId(1))

            assertEquals(AddResult(added = 3, alreadyThere = 0), result)
            assertEquals(ids(11, 12, 13), playlists.storedSongs(id))
        }

    @Test
    fun `a whole artist is added album by album from the artist's own songs`() =
        runTest {
            val second = song("x", track = 1, id = 22).copy(albumId = AlbumId(2), albumTitle = "Beta")
            val first = song("y", track = 1, id = 21).copy(albumId = AlbumId(1), albumTitle = "Alpha")
            val reader = ArtistReader(ArtistId(1), listOf(second, first))
            val id = newPlaylist(22)

            val result = AddArtistToPlaylist(reader, add)(id, ArtistId(1))

            assertEquals(AddResult(added = 1, alreadyThere = 1), result)
            assertEquals(ids(22, 21), playlists.storedSongs(id))
        }

    @Test
    fun `an album with no songs adds nothing`() =
        runTest {
            val id = newPlaylist()

            val result = AddAlbumToPlaylist(AlbumReader(AlbumId(9), emptyList()), add)(id, AlbumId(9))

            assertEquals(AddResult(0, 0), result)
        }

    private suspend fun InMemoryPlaylists.observePlaylistsNow() = observePlaylists().first()

    /** A reader that knows one album's songs, in whatever order the test hands them over. */
    private class AlbumReader(
        private val album: AlbumId,
        private val songs: List<Song>,
    ) : SongReader by NoSongs {
        override fun observeAlbumSongs(id: AlbumId): Flow<List<Song>> = flowOf(if (id == album) songs else emptyList())
    }

    private class ArtistReader(
        private val artist: ArtistId,
        private val songs: List<Song>,
    ) : SongReader by NoSongs {
        override fun observeArtistSongs(id: ArtistId): Flow<List<Song>> =
            flowOf(
                if (id ==
                    artist
                ) {
                    songs
                } else {
                    emptyList()
                },
            )
    }

    private object NoSongs : SongReader {
        override fun observeSongs(): Flow<List<Song>> = flowOf(emptyList())

        override fun observeAddedSince(epochSec: Long): Flow<List<Song>> = flowOf(emptyList())

        override fun observeAlbumSongs(id: AlbumId): Flow<List<Song>> = flowOf(emptyList())

        override fun observeArtistSongs(id: ArtistId): Flow<List<Song>> = flowOf(emptyList())

        override fun observeFeaturedOn(id: ArtistId): Flow<List<Song>> = flowOf(emptyList())

        override fun observeFolderSongs(folderPath: String): Flow<List<Song>> = flowOf(emptyList())

        override suspend fun songsByIds(ids: List<SongId>): List<Song> = emptyList()

        override suspend fun allSongRefs() = emptyList<SongRef>()
    }
}
