package dev.tomasflopes.musicplayer.data.analysis

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import dev.tomasflopes.musicplayer.core.analysis.AnalysisBatchRunner
import dev.tomasflopes.musicplayer.core.analysis.AnalysisClock
import dev.tomasflopes.musicplayer.core.analysis.HandBuiltProfilerV1
import dev.tomasflopes.musicplayer.core.analysis.PcmChunkSink
import dev.tomasflopes.musicplayer.core.analysis.PcmDecodeException
import dev.tomasflopes.musicplayer.core.analysis.PcmInfo
import dev.tomasflopes.musicplayer.core.analysis.PcmSource
import dev.tomasflopes.musicplayer.data.db.MusicDatabase
import dev.tomasflopes.musicplayer.data.db.artist
import dev.tomasflopes.musicplayer.data.db.inMemoryDatabase
import dev.tomasflopes.musicplayer.data.db.songEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.PI
import kotlin.math.sin

/**
 * The phone worker against a real (in-memory) Room database and the WorkManager test driver, in `deviceCheck`
 * (ADR 0009, 0059). The decoder is a fake that plays one second of a tone, or fails for named files.
 */
@RunWith(AndroidJUnit4::class)
class AnalysisBatchWorkerTest {
    private lateinit var context: Context
    private lateinit var db: MusicDatabase
    private lateinit var workManager: WorkManager
    private lateinit var scheduler: WorkManagerAnalysisScheduler
    private val profiler = HandBuiltProfilerV1()
    private var now = 0L

    /**
     * Work the scheduler enqueues must not really run here: this worker answers `retry`, so the job stays ENQUEUED
     * (waiting out its back-off) and the test can read its constraints. The real worker is built by hand below.
     */
    private class HoldWorker(
        context: Context,
        params: WorkerParameters,
    ) : Worker(context, params) {
        override fun doWork(): Result = Result.retry()
    }

    private object HoldFactory : WorkerFactory() {
        override fun createWorker(
            appContext: Context,
            workerClassName: String,
            workerParameters: WorkerParameters,
        ): ListenableWorker = HoldWorker(appContext, workerParameters)
    }

    /** One second of a tone per song; a file name in [broken] fails like an undecodable file. */
    private class FakeSource(
        private val broken: Set<String>,
        private val onDecode: () -> Unit,
    ) : PcmSource {
        override fun info(location: String) = PcmInfo(RATE, 1, 1_000L)

        override fun decodeAll(location: String) = error("The worker streams")

        override fun decodeWindow(
            location: String,
            startMs: Long,
            lengthMs: Long,
        ) = error("The worker streams")

        override fun decodeStream(
            location: String,
            sink: PcmChunkSink,
        ) {
            onDecode()
            if (broken.any { location.endsWith("/$it") }) throw PcmDecodeException("Cannot decode $location")
            sink.begin(RATE, 1)
            val samples = FloatArray(RATE) { (0.3 * sin(2 * PI * 440 * it / RATE)).toFloat() }
            sink.accept(samples, samples.size)
        }
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = inMemoryDatabase()
        val configuration = Configuration.Builder().setWorkerFactory(HoldFactory).build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, configuration)
        workManager = WorkManager.getInstance(context)
        scheduler = WorkManagerAnalysisScheduler(workManager, db, profiler)
    }

    @After
    fun tearDown() {
        db.close()
    }

    /** Song rows are named by their MediaStore id, so a broken song is picked by that id. */
    private suspend fun addSongs(count: Int): List<Long> {
        val artist = db.artist("Artist")
        return (1..count).map { db.songScanDao().insert(songEntity("w$it.mp3", artist, dateAdded = it.toLong())) }
    }

    private fun worker(
        broken: Set<Long> = emptySet(),
        msPerSong: Long = 0L,
        budgetMs: Long = Long.MAX_VALUE / 2,
    ): ListenableWorker {
        val clock = AnalysisClock { now }
        val source = FakeSource(broken.map { it.toString() }.toSet()) { now += msPerSong }
        val runner =
            AnalysisBatchRunner(
                source,
                profiler,
                RoomAnalysisQueue(db, profiler),
                RoomAnalysisStore(db, profiler),
                clock,
            )
        val factory =
            object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters,
                ): ListenableWorker =
                    AnalysisBatchWorker(appContext, workerParameters, runner, BudgetedClock(clock, budgetMs), scheduler)
            }
        return TestListenableWorkerBuilder<AnalysisBatchWorker>(context).setWorkerFactory(factory).build()
    }

    /** Lets a test shrink the nine-minute budget: the worker reads "now" once, to set its deadline. */
    private class BudgetedClock(
        private val inner: AnalysisClock,
        private val budgetMs: Long,
    ) : AnalysisClock {
        private var first = true

        override fun nowMs(): Long {
            val value = inner.nowMs()
            if (!first) return value
            first = false
            // The deadline is now + 9 minutes; moving "now" back by (9 minutes - budget) makes it now + budget.
            return value - (NINE_MINUTES_MS - budgetMs).coerceAtLeast(0L)
        }
    }

    private suspend fun uniqueWork(): List<WorkInfo> =
        workManager.getWorkInfosForUniqueWorkFlow(WorkManagerAnalysisScheduler.UNIQUE_NAME).first()

    @Test
    fun aBatchAnalysesEveryQueuedSongAndWritesPhoneProfiles() =
        runTest {
            val ids = addSongs(3)

            val result = (worker().startWork().get())

            assertEquals(ListenableWorker.Result.success(), result)
            assertEquals(0, db.soundProfileDao().countWaiting(1, 3))
            val rows =
                db.openHelper.readableDatabase
                    .query("SELECT song_id, source, file_size, profiler_version FROM sound_profile ORDER BY song_id")
                    .use { c ->
                        generateSequence {
                            if (c.moveToNext()) {
                                listOf(
                                    c.getLong(0),
                                    c.getString(1),
                                    c.getLong(2),
                                    c.getInt(3),
                                )
                            } else {
                                null
                            }
                        }.toList()
                    }
            assertEquals(ids.map { listOf(it, "phone", 100L, 1) }, rows)
            assertTrue("nothing left, so no follow-up", uniqueWork().isEmpty())
        }

    @Test
    fun aSongThatCannotBeDecodedIsCountedAndTheBatchChainsItself() =
        runTest {
            val ids = addSongs(2)
            val bad = ids[0]
            val mediaStoreId =
                db.openHelper.readableDatabase
                    .query("SELECT media_store_id FROM song WHERE id = $bad")
                    .use { cursor ->
                        cursor.moveToFirst()
                        cursor.getLong(0)
                    }

            worker(broken = setOf(mediaStoreId)).startWork().get()

            assertEquals(1, db.soundProfileDao().failedAttempts(bad, 1))
            assertEquals(1, db.soundProfileDao().countWaiting(1, 3))
            assertEquals(WorkInfo.State.ENQUEUED, uniqueWork().single().state)
        }

    @Test
    fun aBatchThatRunsOutOfTimeLeavesTheRestAndChainsItself() =
        runTest {
            addSongs(3)

            worker(msPerSong = 1_000L, budgetMs = 1_500L).startWork().get()

            assertEquals(1, db.soundProfileDao().countWaiting(1, 3))
            assertEquals(WorkInfo.State.ENQUEUED, uniqueWork().single().state)
        }

    @Test
    fun aRunThatDidNothingDoesNotChainItself() =
        runTest {
            worker().startWork().get()

            assertTrue(uniqueWork().isEmpty())
        }

    @Test
    fun requestStartsNothingWhenNoSongWaits() =
        runTest {
            scheduler.request()

            assertTrue(uniqueWork().isEmpty())
        }

    @Test
    fun aShortQueueNeedsNoConstraints() =
        runTest {
            addSongs(3)

            scheduler.request()

            val info = uniqueWork().single()
            assertFalse(info.constraints.requiresCharging())
        }

    @Test
    fun aLongQueueWaitsForTheCharger() =
        runTest {
            addSongs(101)

            scheduler.request()

            assertTrue(uniqueWork().single().constraints.requiresCharging())
        }

    @Test
    fun askingTwiceLeavesOneJobAndTheConstraintFollowsTheQueue() =
        runTest {
            addSongs(101)
            scheduler.request()
            assertTrue(uniqueWork().single().constraints.requiresCharging())
            db.openHelper.writableDatabase.execSQL("UPDATE song SET in_library = 0 WHERE id > 5")

            scheduler.request()

            val info = uniqueWork().single { it.state == WorkInfo.State.ENQUEUED }
            assertFalse(info.constraints.requiresCharging())
        }

    private companion object {
        const val RATE = 8_000
        const val NINE_MINUTES_MS = 9 * 60 * 1_000L
    }
}
