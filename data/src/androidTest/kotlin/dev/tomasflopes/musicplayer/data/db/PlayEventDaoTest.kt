package dev.tomasflopes.musicplayer.data.db

import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.tomasflopes.musicplayer.core.model.SongId
import dev.tomasflopes.musicplayer.core.model.log.EndedBy
import dev.tomasflopes.musicplayer.core.model.log.PlayEvent
import dev.tomasflopes.musicplayer.core.model.log.PlaySource
import dev.tomasflopes.musicplayer.core.model.log.SearchEvent
import dev.tomasflopes.musicplayer.core.model.log.SearchItemType
import dev.tomasflopes.musicplayer.core.model.log.StartedBy
import dev.tomasflopes.musicplayer.data.db.entity.PlayEventEntity
import dev.tomasflopes.musicplayer.data.db.entity.SearchEventEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlayEventDaoTest {
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
    fun everyLogValueRoundTrips() =
        runTest {
            var time = 0L
            val events =
                PlaySource.entries.flatMap { source ->
                    EndedBy.entries.flatMap { endedBy ->
                        StartedBy.entries.flatMap { startedBy ->
                            listOf(false, true).map { shuffle ->
                                time++
                                PlayEvent(
                                    songId = SongId(time),
                                    startedAtEpochMs = time,
                                    listenedMs = time * 10,
                                    songDurationMs = 200_000,
                                    endedBy = endedBy,
                                    source = source,
                                    startedBy = startedBy,
                                    shuffle = shuffle,
                                    mixId = time,
                                )
                            }
                        }
                    }
                }
            events.forEach { db.playEventDao().insert(PlayEventEntity.from(it)) }

            val readBack =
                db
                    .playEventDao()
                    .observeRecent(events.size + 10)
                    .first()
                    .map { it.toDomain() }

            assertEquals(events.size, readBack.size)
            assertEquals(events.toSet(), readBack.toSet())
            assertEquals(events.size, db.playEventDao().observeCount().first())
        }

    @Test
    fun recentIsNewestFirstAndLimited() =
        runTest {
            (1L..5L).forEach { at ->
                val event = PlayEvent(SongId(at), at, 1, 1, EndedBy.COMPLETED, PlaySource.ALBUM, StartedBy.AUTO, false)
                db.playEventDao().insert(PlayEventEntity.from(event))
            }

            assertEquals(
                listOf(5L, 4L),
                db
                    .playEventDao()
                    .observeRecent(2)
                    .first()
                    .map { it.startedAt },
            )
        }

    @Test
    fun searchEventsAreStored() =
        runTest {
            db.searchEventDao().insert(SearchEventEntity.from(SearchEvent("ed", SearchItemType.ARTIST, 7, 1_000)))

            assertEquals(1, db.searchEventDao().count())
        }
}
