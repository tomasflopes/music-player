package dev.tomasflopes.musicplayer.radio

import dev.tomasflopes.musicplayer.core.model.ArtistId
import dev.tomasflopes.musicplayer.core.model.SongId
import dev.tomasflopes.musicplayer.core.model.log.PlaySource
import dev.tomasflopes.musicplayer.core.model.ports.PlayedSinceSource
import dev.tomasflopes.musicplayer.core.model.ports.SoundVectorSource
import dev.tomasflopes.musicplayer.core.model.sound.SoundSpace
import dev.tomasflopes.musicplayer.core.taste.config.RadioConfig
import dev.tomasflopes.musicplayer.core.taste.lists.ArtistRadioPolicy
import dev.tomasflopes.musicplayer.fakes.FakePlaybackController
import dev.tomasflopes.musicplayer.fakes.FakeSongReader
import dev.tomasflopes.musicplayer.fakes.testSong
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class StartArtistRadioTest {
    private class FakeVectors(
        private val space: SoundSpace,
    ) : SoundVectorSource {
        override suspend fun space(): SoundSpace = space
    }

    private class NoRecentPlays : PlayedSinceSource {
        override fun observePlayedIdsSince(sinceEpochMs: Long): Flow<Set<SongId>> = flowOf(emptySet())
    }

    private class RecordingMessages : ShortMessages {
        val shown = mutableListOf<String>()

        override fun show(text: String) {
            shown += text
        }
    }

    private val playback = FakePlaybackController()
    private val messages = RecordingMessages()
    private val clock = Clock.fixed(Instant.parse("2026-11-16T12:00:00Z"), ZoneOffset.UTC)
    private val config = RadioConfig(seed = 7)

    /** Songs 1..40; songs 1 and 2 are by artist 100, the others by an artist of their own. */
    private val songs =
        (1L..40L).map {
            testSong(it, artistId = if (it <= 2) 100 else it, title = "Title $it", artistName = "Artist $it")
        }

    private fun radio(profiled: List<Long> = songs.map { it.id.value }) =
        StartArtistRadio(
            songs = FakeSongReader(songs = songs),
            vectors = FakeVectors(SoundSpace(profiled.toLongArray(), 1, profiled.map { it.toFloat() }.toFloatArray())),
            played = NoRecentPlays(),
            playback = playback,
            messages = messages,
            policy = ArtistRadioPolicy(config),
            config = config,
            clock = clock,
        )

    @Test
    fun `plays one batch as a radio request`() =
        runTest {
            radio()(ArtistId(100))

            val request = playback.requests.single()
            assertEquals(0, request.startIndex)
            assertEquals(false, request.shuffle)
            assertEquals(PlaySource.RADIO, request.source)
            assertEquals(true, request.picked)
            assertEquals(25, request.songs.size)
            assertTrue(messages.shown.isEmpty())
        }

    @Test
    fun `an artist with no sound profile plays nothing and says so`() =
        runTest {
            radio(profiled = (3L..40L).toList())(ArtistId(100))

            assertTrue(playback.requests.isEmpty())
            assertEquals(listOf(StartArtistRadio.NO_PROFILE), messages.shown)
        }

    @Test
    fun `an empty space plays nothing and says so`() =
        runTest {
            radio(profiled = emptyList())(ArtistId(100))

            assertTrue(playback.requests.isEmpty())
            assertEquals(listOf("No sound profiles for this artist yet"), messages.shown)
        }
}
