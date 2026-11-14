package dev.tomasflopes.musicplayer.data.db

import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.tomasflopes.musicplayer.data.db.entity.PlaylistEntity
import dev.tomasflopes.musicplayer.data.db.entity.PlaylistSongEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlaylistDaoTest {
    private lateinit var db: MusicDatabase

    @Before
    fun open() {
        db = inMemoryDatabase()
    }

    @After
    fun close() {
        db.close()
    }

    private suspend fun playlist(
        name: String = "Gym",
        lastUsed: Long = 0,
    ) = db.playlistDao().insert(PlaylistEntity(name = name, createdAt = 1, updatedAt = 1, lastUsedAt = lastUsed))

    private suspend fun song(
        name: String,
        inLibrary: Boolean = true,
    ): Long = db.songScanDao().insert(songEntity("$name.mp3", db.artist("A $name"), inLibrary = inLibrary))

    private suspend fun add(
        playlist: Long,
        song: Long,
        position: Int,
    ) = db.playlistDao().insertSongs(listOf(PlaylistSongEntity(playlist, song, position, addedAt = 1)))

    @Test
    fun songsComeBackInPositionOrder() =
        runTest {
            val list = playlist()
            val (a, b, c) = listOf(song("a"), song("b"), song("c"))
            add(list, b, 0)
            add(list, c, 1)
            add(list, a, 2)

            val titles =
                db
                    .playlistDao()
                    .observeSongs(list)
                    .first()
                    .map { it.song.title }

            assertEquals(listOf("b", "c", "a"), titles)
        }

    @Test
    fun aRetiredSongIsHiddenKeepsItsPlaceAndComesBackWithTheFile() =
        runTest {
            val list = playlist()
            val (a, b, c) = listOf(song("a"), song("b"), song("c"))
            listOf(a, b, c).forEachIndexed { index, id -> add(list, id, index) }

            db.songScanDao().setInLibrary(b, false)
            assertEquals(
                listOf("a", "c"),
                db
                    .playlistDao()
                    .observeSongs(list)
                    .first()
                    .map { it.song.title },
            )
            assertEquals(
                2,
                db
                    .playlistDao()
                    .observe(list)
                    .first()
                    ?.songCount,
            )

            db.songScanDao().setInLibrary(b, true)
            assertEquals(
                listOf("a", "b", "c"),
                db
                    .playlistDao()
                    .observeSongs(list)
                    .first()
                    .map { it.song.title },
            )
        }

    @Test
    fun addingTheSameSongTwiceKeepsTheFirstRow() =
        runTest {
            val list = playlist()
            val a = song("a")

            add(list, a, 0)
            val second = add(list, a, 5)

            assertEquals(listOf(-1L), second)
            assertEquals(listOf(0), db.playlistDao().visibleSlots(list).map { it.position })
        }

    @Test
    fun lastPositionCountsRetiredSongsAndIsMinusOneWhenEmpty() =
        runTest {
            val list = playlist()
            assertEquals(-1, db.playlistDao().lastPosition(list))

            val a = song("a", inLibrary = false)
            add(list, a, 3)

            assertEquals(3, db.playlistDao().lastPosition(list))
        }

    @Test
    fun playlistsAreListedMostRecentlyUsedFirstWithCountAndCover() =
        runTest {
            val old = playlist("Old", lastUsed = 10)
            val recent = playlist("Recent", lastUsed = 20)
            val a = song("a")
            add(old, a, 0)

            val rows = db.playlistDao().observeAll().first()

            assertEquals(listOf("Recent", "Old"), rows.map { it.playlist.name })
            assertEquals(listOf(0, 1), rows.map { it.songCount })
            assertEquals(listOf(null, a), rows.map { it.coverSongId })
            assertEquals(recent, rows.first().playlist.id)
        }

    @Test
    fun deletingAPlaylistTakesItsSongsAndNothingElse() =
        runTest {
            val gone = playlist("Gone")
            val kept = playlist("Kept")
            val a = song("a")
            add(gone, a, 0)
            add(kept, a, 0)

            db.playlistDao().delete(gone)

            assertNull(db.playlistDao().observe(gone).first())
            assertEquals(1, db.playlistDao().visibleSlots(kept).size)
            assertEquals(0, db.playlistDao().visibleSlots(gone).size)
        }
}
