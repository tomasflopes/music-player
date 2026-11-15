package dev.tomasflopes.musicplayer.data.db

import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.tomasflopes.musicplayer.core.analysis.ImportTarget
import dev.tomasflopes.musicplayer.core.analysis.ProfileBlobCodec
import dev.tomasflopes.musicplayer.core.analysis.SoundProfile
import dev.tomasflopes.musicplayer.data.db.dao.AnalysisProgressRow
import dev.tomasflopes.musicplayer.data.db.dao.AnalysisSummaryRow
import dev.tomasflopes.musicplayer.data.db.entity.AnalysisFailureEntity
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

    private suspend fun songsAdded(vararg dateAdded: Long): List<Long> {
        val artist = db.artist("Artist")
        return dateAdded.mapIndexed { index, date ->
            db.songScanDao().insert(songEntity("q$index.mp3", artist, dateAdded = date))
        }
    }

    private suspend fun queued(
        skip: List<Long> = emptyList(),
        limit: Int = 50,
    ) = db.soundProfileDao().nextQueued(1, 3, skip, limit)

    @Test
    fun theQueueListsLibrarySongsWithoutAProfileNewestFirst() =
        runTest {
            val ids = songsAdded(10, 30, 20)

            val rows = queued()

            assertEquals(listOf(ids[1], ids[2], ids[0]), rows.map { it.id })
            assertEquals(listOf(100L, 100L, 100L), rows.map { it.fileSize })
        }

    @Test
    fun aProfileOfTheSameFileAndVersionLeavesTheQueueButAnotherSizeOrVersionDoesNot() =
        runTest {
            val ids = songsAdded(1, 2, 3, 4)
            db.soundProfileDao().upsert(entity(ids[0]))
            db.soundProfileDao().upsert(entity(ids[1], size = 999))
            db.soundProfileDao().upsert(entity(ids[2], version = 2))

            assertEquals(setOf(ids[1], ids[2], ids[3]), queued().map { it.id }.toSet())
        }

    @Test
    fun retiredSongsAreNotQueued() =
        runTest {
            val ids = songs(true, false)

            assertEquals(listOf(ids[0]), queued().map { it.id })
        }

    @Test
    fun aSongIsGivenUpOnAfterThreeFailedAttempts() =
        runTest {
            val ids = songsAdded(1, 2, 3)
            val dao = db.soundProfileDao()
            dao.upsertFailure(AnalysisFailureEntity(ids[0], 1, attempts = 2, lastError = "x"))
            dao.upsertFailure(AnalysisFailureEntity(ids[1], 1, attempts = 3, lastError = "x"))
            dao.upsertFailure(AnalysisFailureEntity(ids[2], 2, attempts = 3, lastError = "other version"))

            val rows = queued()

            assertEquals(setOf(ids[0], ids[2]), rows.map { it.id }.toSet())
            assertEquals(2, rows.first { it.id == ids[0] }.attempts)
            assertEquals(0, rows.first { it.id == ids[2] }.attempts)
        }

    @Test
    fun skippedIdsAndTheLimitAreHonoured() =
        runTest {
            val ids = songsAdded(1, 2, 3, 4)

            assertEquals(listOf(ids[3], ids[2]), queued(limit = 2).map { it.id })
            assertEquals(listOf(ids[2], ids[1]), queued(skip = listOf(ids[3]), limit = 2).map { it.id })
            assertEquals(emptyList<Long>(), queued(skip = ids))
        }

    @Test
    fun countWaitingMatchesTheQueue() =
        runTest {
            val ids = songsAdded(1, 2, 3)
            val dao = db.soundProfileDao()
            dao.upsert(entity(ids[0]))
            dao.upsertFailure(AnalysisFailureEntity(ids[1], 1, attempts = 3, lastError = "x"))

            assertEquals(1, dao.countWaiting(1, 3))
            assertEquals(queued().size, dao.countWaiting(1, 3))
        }

    @Test
    fun progressCountsAnalysedWaitingAndGivenUp() =
        runTest {
            val ids = songsAdded(1, 2, 3, 4)
            val dao = db.soundProfileDao()
            dao.upsert(entity(ids[0]))
            dao.upsertFailure(AnalysisFailureEntity(ids[1], 1, attempts = 3, lastError = "x"))
            dao.upsertFailure(AnalysisFailureEntity(ids[2], 1, attempts = 1, lastError = "x"))

            assertEquals(AnalysisProgressRow(profiled = 1, waiting = 2, gaveUp = 1), dao.observeProgress(1, 3).first())
        }

    @Test
    fun aFailureCountsUpAndCanBeCleared() =
        runTest {
            val id = songsAdded(1).single()
            val dao = db.soundProfileDao()
            assertEquals(null, dao.failedAttempts(id, 1))

            dao.upsertFailure(AnalysisFailureEntity(id, 1, attempts = 1, lastError = "first"))
            dao.upsertFailure(AnalysisFailureEntity(id, 1, attempts = 2, lastError = "second"))
            assertEquals(2, dao.failedAttempts(id, 1))

            dao.deleteFailures(id)
            assertEquals(null, dao.failedAttempts(id, 1))
        }

    @Test
    fun theImportedSampleHoldsMacProfilesOfTheCurrentFileOnly() =
        runTest {
            val ids = songsAdded(1, 2, 3, 4)
            val dao = db.soundProfileDao()
            dao.upsert(entity(ids[0]))
            dao.upsert(entity(ids[1]).copy(source = "phone"))
            dao.upsert(entity(ids[2], size = 999))

            val sample = dao.importedSample(1, 20)

            assertEquals(listOf(ids[0]), sample.map { it.songId })
        }
}
