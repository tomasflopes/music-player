package dev.tomasflopes.musicplayer.data.db

import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.tomasflopes.musicplayer.core.model.SongId
import dev.tomasflopes.musicplayer.data.db.entity.SongFeatureEntity
import dev.tomasflopes.musicplayer.data.repo.RoomFeatureReader
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SongFeatureDaoTest {
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
    fun allNamesGroupsResolvedAndRawArtistsBySong() =
        runTest {
            val ed = db.artist("Ed")
            db.songFeatureDao().insert(SongFeatureEntity(songId = 1, artistId = ed, rawName = null))
            db.songFeatureDao().insert(SongFeatureEntity(songId = 1, artistId = null, rawName = "Unknown Guest"))
            db.songFeatureDao().insert(SongFeatureEntity(songId = 2, artistId = ed, rawName = null))

            val names = RoomFeatureReader(db).all()

            assertEquals(mapOf(SongId(1) to listOf("Ed", "Unknown Guest"), SongId(2) to listOf("Ed")), names)
        }

    @Test
    fun namesForKeepsTitleOrder() =
        runTest {
            val ed = db.artist("Ed")
            db.songFeatureDao().insert(SongFeatureEntity(songId = 1, artistId = ed, rawName = null))
            db.songFeatureDao().insert(SongFeatureEntity(songId = 1, artistId = null, rawName = "Guest"))

            assertEquals(listOf("Ed", "Guest"), RoomFeatureReader(db).namesFor(SongId(1)))
        }
}
