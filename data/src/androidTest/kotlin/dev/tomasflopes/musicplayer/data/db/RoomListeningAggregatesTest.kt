package dev.tomasflopes.musicplayer.data.db

import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.tomasflopes.musicplayer.core.model.log.PlayEvent
import dev.tomasflopes.musicplayer.core.model.stats.AggregatesFactory
import dev.tomasflopes.musicplayer.core.model.stats.ContractSong
import dev.tomasflopes.musicplayer.core.model.stats.ListeningAggregatesContract
import dev.tomasflopes.musicplayer.data.db.entity.AlbumEntity
import dev.tomasflopes.musicplayer.data.db.entity.ArtistEntity
import dev.tomasflopes.musicplayer.data.db.entity.PlayEventEntity
import dev.tomasflopes.musicplayer.data.repo.RoomListeningAggregates
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** The Room aggregates meet the same contract as the in-memory reference (ADR 0088). Runs under `deviceCheck`. */
@RunWith(AndroidJUnit4::class)
class RoomListeningAggregatesTest {
    private val factory =
        AggregatesFactory { songs, plays ->
            val db = inMemoryDatabase()
            seed(db, songs, plays)
            RoomListeningAggregates(db)
        }

    private fun seed(
        db: MusicDatabase,
        songs: List<ContractSong>,
        plays: List<PlayEvent>,
    ) = runBlocking {
        songs.map { it.artistId }.distinct().forEach {
            db.artistDao().insert(
                ArtistEntity(it, "artist $it", "artist $it"),
            )
        }
        songs
            .mapNotNull {
                it.albumId
            }.distinct()
            .forEach { db.albumDao().insert(AlbumEntity(it, "album $it", "x", "album $it")) }
        songs.forEach { song ->
            db.songScanDao().insert(
                songEntity("song${song.songId}.mp3", song.artistId, song.albumId).copy(id = song.songId),
            )
        }
        plays.forEach { db.playEventDao().insert(PlayEventEntity.from(it)) }
    }

    @Test
    fun roomMeetsTheListeningAggregatesContract() {
        val failures =
            ListeningAggregatesContract.checks(factory).mapNotNull { check ->
                runCatching { check.run() }.exceptionOrNull()?.let { "${check.name}: ${it.message}" }
            }

        assertTrue("Failed checks:\n${failures.joinToString("\n")}", failures.isEmpty())
    }
}
