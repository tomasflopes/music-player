package dev.tomasflopes.musicplayer.data.db

import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.tomasflopes.musicplayer.core.model.SongId
import dev.tomasflopes.musicplayer.core.model.log.EndedBy
import dev.tomasflopes.musicplayer.core.model.log.PlayEvent
import dev.tomasflopes.musicplayer.core.model.log.PlaySource
import dev.tomasflopes.musicplayer.core.model.log.StartedBy
import dev.tomasflopes.musicplayer.core.model.mixes.MixId
import dev.tomasflopes.musicplayer.core.model.mixes.MixSeed
import dev.tomasflopes.musicplayer.core.model.mixes.MixSeedKind
import dev.tomasflopes.musicplayer.data.db.entity.PlayEventEntity
import dev.tomasflopes.musicplayer.data.repo.RoomMixReader
import dev.tomasflopes.musicplayer.data.repo.RoomMixStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MixDaoTest {
    private lateinit var db: MusicDatabase
    private lateinit var store: RoomMixStore
    private lateinit var reader: RoomMixReader

    @Before
    fun open() {
        db = inMemoryDatabase()
        store = RoomMixStore(db)
        reader = RoomMixReader(db)
    }

    @After
    fun close() {
        db.close()
    }

    private suspend fun song(
        name: String,
        inLibrary: Boolean = true,
    ): Long = db.songScanDao().insert(songEntity("$name.mp3", db.artist("A $name"), inLibrary = inLibrary))

    private fun songSeed(id: Long) = MixSeed(MixSeedKind.SONG, id)

    private suspend fun seedsOf(id: MixId) = reader.mix(id)?.seeds

    @Test
    fun aMixKeepsItsSeedsInThePickedOrder() =
        runTest {
            val (a, b, c) = listOf(song("a"), song("b"), song("c"))
            val artist = db.artist("Band")
            val seeds = listOf(songSeed(c), MixSeed(MixSeedKind.ARTIST, artist), songSeed(a), songSeed(b))

            val id = store.create("Run", seeds, nowEpochMs = 5)

            assertEquals(seeds, seedsOf(id))
            assertEquals("Run", reader.mix(id)?.name)
            assertEquals(5L, reader.mix(id)?.createdAtEpochMs)
        }

    @Test
    fun mixesComeOldestFirst() =
        runTest {
            val first = store.create("One", listOf(songSeed(song("a"))), nowEpochMs = 1)
            val second = store.create("Two", listOf(songSeed(song("b"))), nowEpochMs = 2)

            assertEquals(listOf(first, second), reader.observeMixes().first().map { it.id })
        }

    @Test
    fun replacingSeedsSwapsThemAllAtOnce() =
        runTest {
            val (a, b, c) = listOf(song("a"), song("b"), song("c"))
            val id = store.create("Run", listOf(songSeed(a), songSeed(b)), nowEpochMs = 1)

            store.replaceSeeds(id, listOf(songSeed(c), songSeed(a)), nowEpochMs = 9)

            assertEquals(listOf(songSeed(c), songSeed(a)), seedsOf(id))
            assertEquals(9L, reader.mix(id)?.updatedAtEpochMs)
            assertEquals(2, db.mixDao().rawSeedCount(id.value))
        }

    @Test
    fun theDeleteAndTheInsertOfAReplaceRollBackTogether() =
        runTest {
            val (a, b) = listOf(song("a"), song("b"))
            val id = store.create("Run", listOf(songSeed(a), songSeed(b)), nowEpochMs = 1)

            // The store runs these two steps in one transaction; an error between them must undo the delete.
            runCatching {
                db.withTransaction {
                    db.mixDao().deleteVisibleSeeds(id.value)
                    error("stop between the delete and the insert")
                }
            }

            assertEquals(listOf(songSeed(a), songSeed(b)), seedsOf(id))
        }

    @Test
    fun renamingKeepsTheSeeds() =
        runTest {
            val id = store.create("Run", listOf(songSeed(song("a"))), nowEpochMs = 1)

            store.rename(id, "Walk", nowEpochMs = 4)

            assertEquals("Walk", reader.mix(id)?.name)
            assertEquals(1, seedsOf(id)?.size)
        }

    @Test
    fun deletingAMixRemovesItsSeedsAndKeepsTheLog() =
        runTest {
            val a = song("a")
            val id = store.create("Run", listOf(songSeed(a)), nowEpochMs = 1)
            db.playEventDao().insert(PlayEventEntity.from(mixEvent(a, id.value)))

            store.delete(id)

            assertNull(reader.mix(id))
            assertEquals(0, db.mixDao().rawSeedCount(id.value))
            assertEquals(
                listOf(id.value),
                db
                    .playEventDao()
                    .observeRecent(10)
                    .first()
                    .map { it.mixId },
            )
        }

    @Test
    fun aSeedWhoseSongLeftTheLibraryIsHiddenNotRemoved() =
        runTest {
            val (stays, leaves) = listOf(song("stays"), song("leaves"))
            val artist = db.artist("Band")
            val id =
                store.create(
                    "Run",
                    listOf(songSeed(stays), songSeed(leaves), MixSeed(MixSeedKind.ARTIST, artist)),
                    1,
                )

            db.songScanDao().setInLibrary(leaves, false)

            assertEquals(listOf(songSeed(stays), MixSeed(MixSeedKind.ARTIST, artist)), seedsOf(id))
            assertEquals(3, db.mixDao().rawSeedCount(id.value))

            db.songScanDao().setInLibrary(leaves, true)
            assertEquals(3, seedsOf(id)?.size)
        }

    @Test
    fun replacingSeedsKeepsTheHiddenOnes() =
        runTest {
            val (stays, leaves, other) = listOf(song("stays"), song("leaves"), song("other"))
            val id = store.create("Run", listOf(songSeed(stays), songSeed(leaves)), nowEpochMs = 1)
            db.songScanDao().setInLibrary(leaves, false)

            store.replaceSeeds(id, listOf(songSeed(other)), nowEpochMs = 2)

            assertEquals(listOf(songSeed(other)), seedsOf(id))
            assertEquals(2, db.mixDao().rawSeedCount(id.value))
        }

    @Test
    fun theMixIdRoundTripsThroughThePlayLog() =
        runTest {
            val a = song("a")
            db.playEventDao().insert(PlayEventEntity.from(mixEvent(a, mixId = 12)))

            val read =
                db
                    .playEventDao()
                    .observeRecent(1)
                    .first()
                    .single()
                    .toDomain()

            assertEquals(12L, read.mixId)
            assertEquals(PlaySource.MIX, read.source)
        }

    private fun mixEvent(
        songId: Long,
        mixId: Long,
    ) = PlayEvent(
        songId = SongId(songId),
        startedAtEpochMs = 1,
        listenedMs = 10,
        songDurationMs = 20,
        endedBy = EndedBy.COMPLETED,
        source = PlaySource.MIX,
        startedBy = StartedBy.PICKED,
        shuffle = false,
        mixId = mixId,
    )
}
