package dev.tomasflopes.musicplayer.data.db

import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.tomasflopes.musicplayer.core.analysis.ImportTarget
import dev.tomasflopes.musicplayer.core.analysis.ProfileBlobCodec
import dev.tomasflopes.musicplayer.core.analysis.SoundProfile
import dev.tomasflopes.musicplayer.data.db.dao.AnalysisSummaryRow
import dev.tomasflopes.musicplayer.data.db.entity.SoundProfileEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SoundProfileDaoTest {
    private lateinit var db: MusicDatabase

    @Before
    fun open() {
        db = inMemoryDatabase()
    }

    @After
    fun close() {
        db.close()
    }

    private fun entity(
        songId: Long,
        size: Long = 100,
        version: Int = 1,
        features: ByteArray = ProfileBlobCodec.encode(SoundProfile(FloatArray(SoundProfile.FIXED_SIZE), FloatArray(3))),
    ) = SoundProfileEntity(songId, version, features, "mac", size, 5)

    private suspend fun songs(vararg inLibrary: Boolean): List<Long> {
        val artist = db.artist("Artist")
        return inLibrary.mapIndexed { index, live ->
            db.songScanDao().insert(songEntity("s$index.mp3", artist, inLibrary = live))
        }
    }

    @Test
    fun everyLibrarySongIsEitherProfiledOrQueued() =
        runTest {
            val ids = songs(true, true, true, false)
            db.soundProfileDao().upsert(entity(ids[0]))

            val summary = db.soundProfileDao().observeSummary(1).first()

            assertEquals(AnalysisSummaryRow(profiled = 1, queued = 2), summary)
        }

    @Test
    fun aProfileOfAnotherSizeOrVersionLeavesTheSongQueued() =
        runTest {
            val ids = songs(true, true, true)
            db.soundProfileDao().upsert(entity(ids[0], size = 999))
            db.soundProfileDao().upsert(entity(ids[1], version = 2))
            db.soundProfileDao().upsert(entity(ids[2]))

            assertEquals(AnalysisSummaryRow(profiled = 1, queued = 2), db.soundProfileDao().observeSummary(1).first())
        }

    @Test
    fun aRetiredSongsProfileIsNotCounted() =
        runTest {
            val ids = songs(true, false)
            db.soundProfileDao().upsert(entity(ids[1]))

            assertEquals(AnalysisSummaryRow(profiled = 0, queued = 1), db.soundProfileDao().observeSummary(1).first())
        }

    @Test
    fun writingTheSameProfileTwiceChangesNothing() =
        runTest {
            val ids = songs(true, true)
            val dao = db.soundProfileDao()
            dao.upsert(entity(ids[0]))
            val first = dao.observeSummary(1).first()

            dao.upsert(entity(ids[0]))

            assertEquals(first, dao.observeSummary(1).first())
            assertEquals(AnalysisSummaryRow(profiled = 1, queued = 1), first)
        }

    @Test
    fun theBlobComesBackByteForByte() =
        runTest {
            val ids = songs(true)
            val bytes =
                ProfileBlobCodec.encode(
                    SoundProfile(
                        FloatArray(SoundProfile.FIXED_SIZE) {
                            it * 0.5f
                        },
                        floatArrayOf(1f, 2f),
                    ),
                )
            db.soundProfileDao().upsert(entity(ids[0], features = bytes))

            val stored =
                db.openHelper.readableDatabase
                    .query("SELECT features FROM sound_profile WHERE song_id = ${ids[0]}")
                    .use { cursor ->
                        cursor.moveToFirst()
                        cursor.getBlob(0)
                    }

            assertArrayEquals(bytes, stored)
        }

    @Test
    fun importTargetsListEverySongRetiredOnesIncluded() =
        runTest {
            songs(true, false)

            val targets =
                db.soundProfileDao().importTargets().map {
                    ImportTarget(
                        it.id,
                        it.path,
                        it.fileSize,
                        it.inLibrary,
                    )
                }

            assertEquals(listOf(true, false), targets.sortedBy { it.songId }.map { it.inLibrary })
        }
}
