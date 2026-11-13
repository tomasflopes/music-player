package dev.tomasflopes.musicplayer.data.db

import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.tomasflopes.musicplayer.data.db.entity.SongFeatureEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SongDaoTest {
    private lateinit var db: MusicDatabase

    @Before
    fun open() {
        db = inMemoryDatabase()
    }

    @After
    fun close() {
        db.close()
    }

    @Test
    fun albumSongsComeBackInDiscThenTrackOrder() =
        runTest {
            val artist = db.artist("A")
            val album = db.album("Album", "A")
            val positions = listOf(2 to 3, 1 to 2, 2 to 1, 1 to 3, 1 to 1, 2 to 2)
            positions.forEach { (disc, track) ->
                db.songDao().insert(songEntity("d$disc t$track.mp3", artist, album, disc = disc, track = track))
            }

            val titles =
                db
                    .songDao()
                    .observeAlbumSongs(album)
                    .first()
                    .map { it.song.title }

            assertEquals(listOf("d1 t1", "d1 t2", "d1 t3", "d2 t1", "d2 t2", "d2 t3"), titles)
        }

    @Test
    fun addedSinceIsInclusiveAndNewestFirst() =
        runTest {
            val artist = db.artist("A")
            listOf(99L, 100L, 101L).forEach { db.songDao().insert(songEntity("$it.mp3", artist, dateAdded = it)) }

            val added =
                db
                    .songDao()
                    .observeAddedSince(100)
                    .first()
                    .map { it.song.dateAdded }

            assertEquals(listOf(101L, 100L), added)
        }

    @Test
    fun rowsCarryArtistNameAndAlbumTitle() =
        runTest {
            val artist = db.artist("Kendrick Lamar")
            val album = db.album("DAMN.", "Kendrick Lamar")
            db.songDao().insert(songEntity("HUMBLE..mp3", artist, album))
            db.songDao().insert(songEntity("Single.mp3", artist))

            val rows = db.songDao().observeAll().first()

            val expected =
                mapOf(
                    "HUMBLE..mp3" to ("Kendrick Lamar" to "DAMN."),
                    "Single.mp3" to ("Kendrick Lamar" to null),
                )
            assertEquals(expected, rows.associate { it.song.fileName to (it.artistName to it.albumTitle) })
        }

    @Test
    fun retiredSongsAreHiddenFromEveryReader() =
        runTest {
            val artist = db.artist("A")
            val album = db.album("Album", "A")
            val live = db.songDao().insert(songEntity("live.mp3", artist, album, dateAdded = 5))
            val retired = db.songDao().insert(songEntity("gone.mp3", artist, album, dateAdded = 5, inLibrary = false))
            listOf(live, retired).forEach {
                db.songFeatureDao().insert(SongFeatureEntity(songId = it, artistId = artist, rawName = "A"))
            }
            val dao = db.songDao()

            val seen =
                listOf(
                    dao.observeAll().first(),
                    dao.observeAddedSince(0).first(),
                    dao.observeAlbumSongs(album).first(),
                    dao.observeArtistSongs(artist).first(),
                    dao.observeFeaturedOn(artist).first(),
                    dao.observeFolderSongs("Music/Album").first(),
                    dao.songsByIds(listOf(live, retired)),
                ).map { rows -> rows.map { it.song.id } }

            seen.forEach { assertEquals(listOf(live), it) }
            assertEquals(listOf(live), dao.allSongRefs().map { it.id })
        }
}
