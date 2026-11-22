package dev.tomasflopes.musicplayer.data.analysis

import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.tomasflopes.musicplayer.core.analysis.HandBuiltProfilerV1
import dev.tomasflopes.musicplayer.core.analysis.ProfileBlobCodec
import dev.tomasflopes.musicplayer.core.analysis.SoundProfile
import dev.tomasflopes.musicplayer.data.db.MusicDatabase
import dev.tomasflopes.musicplayer.data.db.artist
import dev.tomasflopes.musicplayer.data.db.entity.SoundProfileEntity
import dev.tomasflopes.musicplayer.data.db.inMemoryDatabase
import dev.tomasflopes.musicplayer.data.db.songEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The transition data over Room, in `deviceCheck` (ADR 0009, 0092): a current profile of an in-library song is read,
 * and a profile of another version, of a changed file, of a retired song or an unknown id is not.
 *
 * NOT RUN: written without an emulator, never compiled or executed.
 */
@RunWith(AndroidJUnit4::class)
class RoomTransitionProfileSourceTest {
    private val profiler = HandBuiltProfilerV1()
    private lateinit var db: MusicDatabase
    private lateinit var source: RoomTransitionProfileSource

    @Before
    fun open() {
        db = inMemoryDatabase()
        source = RoomTransitionProfileSource(db, profiler)
    }

    @After
    fun close() = db.close()

    private fun fixed() =
        FloatArray(SoundProfile.FIXED_SIZE).also {
            it[SoundProfile.FIRST_BEAT] = 0.5f
            it[SoundProfile.BEAT_TEMPO] = 120f
            it[SoundProfile.TEMPO_CONFIDENCE] = 0.9f
            it[SoundProfile.MUSIC_START] = 0.5f
            it[SoundProfile.INTRO_END] = 10f
            it[SoundProfile.OUTRO_START] = 100f
            it[SoundProfile.MUSIC_END] = 120f
        }

    private fun seed(
        name: String,
        version: Int = profiler.version,
        size: Long = 100L,
        inLibrary: Boolean = true,
    ): Long =
        runBlocking {
            val id = db.songScanDao().insert(songEntity("$name.mp3", db.artist("Artist"), inLibrary = inLibrary))
            val blob = ProfileBlobCodec.encode(SoundProfile(fixed(), FloatArray(3)))
            db.soundProfileDao().upsert(SoundProfileEntity(id, version, blob, "mac", size, 5))
            id
        }

    @Test
    fun readsTheCurrentProfileOfAnInLibrarySong() =
        runBlocking {
            val profile = source.profile(seed("a"))

            assertNotNull(profile)
            assertEquals(120.0, profile!!.tempoBpm, 1e-6)
            assertEquals(500.0, profile.firstBeatMs, 1e-6)
            assertEquals(120_000L, profile.musicEndMs)
        }

    @Test
    fun hasNoProfileForOtherVersionsChangedFilesRetiredSongsOrUnknownIds() =
        runBlocking {
            assertNull(source.profile(seed("old", version = profiler.version + 1)))
            assertNull(source.profile(seed("changed", size = 999L)))
            assertNull(source.profile(seed("retired", inLibrary = false)))
            assertNull(source.profile(987_654L))
        }
}
