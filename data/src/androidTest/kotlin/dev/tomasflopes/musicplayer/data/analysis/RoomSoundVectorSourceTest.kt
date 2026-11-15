package dev.tomasflopes.musicplayer.data.analysis

import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.tomasflopes.musicplayer.core.analysis.HandBuiltProfilerV1
import dev.tomasflopes.musicplayer.core.analysis.ProfileBlobCodec
import dev.tomasflopes.musicplayer.core.analysis.SoundProfile
import dev.tomasflopes.musicplayer.core.analysis.contract.SeededSoundSource
import dev.tomasflopes.musicplayer.core.analysis.contract.SoundFixtureSong
import dev.tomasflopes.musicplayer.core.analysis.contract.SoundSourceFactory
import dev.tomasflopes.musicplayer.core.analysis.contract.SoundVectorSourceContract
import dev.tomasflopes.musicplayer.data.db.MusicDatabase
import dev.tomasflopes.musicplayer.data.db.artist
import dev.tomasflopes.musicplayer.data.db.entity.SoundProfileEntity
import dev.tomasflopes.musicplayer.data.db.inMemoryDatabase
import dev.tomasflopes.musicplayer.data.db.songEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The `SoundVectorSource` contract against Room, in `deviceCheck` (ADR 0009, 0069). Every scenario gets its own
 * in-memory database, so the contract's songs never see each other's rows.
 */
@RunWith(AndroidJUnit4::class)
class RoomSoundVectorSourceTest {
    private val profiler = HandBuiltProfilerV1()
    private val databases = mutableListOf<MusicDatabase>()

    @After
    fun close() {
        databases.forEach { it.close() }
    }

    private val factory =
        SoundSourceFactory { songs ->
            val db = inMemoryDatabase().also { databases += it }
            val ids = seed(db, songs)
            SeededSoundSource(RoomSoundVectorSource(db, profiler)) { ids.getValue(it) }
        }

    /** Song rows are 100 bytes long (`songEntity`); a replaced file is a profile made from another size. */
    private fun seed(
        db: MusicDatabase,
        songs: List<SoundFixtureSong>,
    ): Map<String, Long> =
        runBlocking {
            val artist = db.artist("Artist")
            songs.associate { song ->
                val id = db.songScanDao().insert(songEntity("${song.key}.mp3", artist, inLibrary = song.inLibrary))
                val blob = ProfileBlobCodec.encode(SoundProfile(song.fixed(), FloatArray(3)))
                val version = if (song.currentVersion) profiler.version else profiler.version + 1
                val size = if (song.fileUnchanged) 100L else 999L
                db.soundProfileDao().upsert(SoundProfileEntity(id, version, blob, "mac", size, 5))
                song.key to id
            }
        }

    @Test
    fun roomMeetsTheSoundVectorSourceContract() {
        val failures =
            SoundVectorSourceContract.checks(factory).mapNotNull { check ->
                runCatching { check.run() }.exceptionOrNull()?.let { "${check.name}: ${it.message}" }
            }

        assertTrue("Failed checks:\n${failures.joinToString("\n")}", failures.isEmpty())
    }
}
