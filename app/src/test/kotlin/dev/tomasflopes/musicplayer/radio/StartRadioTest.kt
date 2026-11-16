package dev.tomasflopes.musicplayer.radio

import dev.tomasflopes.musicplayer.core.model.SongId
import dev.tomasflopes.musicplayer.core.model.log.PlaySource
import dev.tomasflopes.musicplayer.core.model.ports.PlayedSinceSource
import dev.tomasflopes.musicplayer.core.model.ports.SoundVectorSource
import dev.tomasflopes.musicplayer.core.model.sound.SoundSpace
import dev.tomasflopes.musicplayer.core.taste.config.RadioConfig
import dev.tomasflopes.musicplayer.core.taste.lists.RadioPolicy
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

class StartRadioTest {
    private class FakeVectors(
        private val space: SoundSpace,
    ) : SoundVectorSource {
        override suspend fun space(): SoundSpace = space
    }

    /** Remembers the moment it was asked about and answers with a fixed set. */
    private class FakePlayedSince(
        private val ids: Set<SongId> = emptySet(),
    ) : PlayedSinceSource {
        val asked = mutableListOf<Long>()

        override fun observePlayedIdsSince(sinceEpochMs: Long): Flow<Set<SongId>> {
            asked += sinceEpochMs
            return flowOf(ids)
        }
    }

    private class RecordingMessages : ShortMessages {
        val shown = mutableListOf<String>()

        override fun show(text: String) {
            shown += text
        }
    }

    private val playback = FakePlaybackController()
    private val messages = RecordingMessages()
    private val now = Instant.parse("2026-11-16T12:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val config = RadioConfig(seed = 7)

    /** Songs 1..40, each by an artist of its own, in a space where song n sits at position n. */
    private val songs = (1L..40L).map { testSong(it, artistId = it, title = "Title $it", artistName = "Artist $it") }

    private fun radio(
        profiled: List<Long> = songs.map { it.id.value },
        played: FakePlayedSince = FakePlayedSince(),
    ) = StartRadio(
        songs = FakeSongReader(songs = songs),
        vectors =
            FakeVectors(
                SoundSpace(profiled.toLongArray(), 1, profiled.map { it.toFloat() }.toFloatArray()),
            ),
        played = played,
        playback = playback,
        messages = messages,
        policy = RadioPolicy(config),
        config = config,
        clock = clock,
    )

    @Test
    fun `plays one batch as a radio request that starts at the seed`() =
        runTest {
            radio()(SongId(5))

            val request = playback.requests.single()
            assertEquals(0, request.startIndex)
            assertEquals(false, request.shuffle)
            assertEquals(PlaySource.RADIO, request.source)
            assertEquals(true, request.picked)
            assertEquals(null, request.playlistId)
            assertEquals(
                5L,
                request.songs
                    .first()
                    .songId.value,
            )
            assertEquals(25, request.songs.size)
            assertTrue(messages.shown.isEmpty())
        }

    @Test
    fun `asks the log about the last two days`() =
        runTest {
            val played = FakePlayedSince()

            radio(played = played)(SongId(5))

            assertEquals(listOf(now.toEpochMilli() - 2 * 86_400_000L), played.asked)
        }

    @Test
    fun `a seed without a sound profile plays nothing and says so`() =
        runTest {
            radio(profiled = (1L..40L).filter { it != 5L })(SongId(5))

            assertTrue(playback.requests.isEmpty())
            assertEquals(listOf("No sound profile for this song yet"), messages.shown)
        }

    @Test
    fun `an empty space plays nothing and says so`() =
        runTest {
            radio(profiled = emptyList())(SongId(5))

            assertTrue(playback.requests.isEmpty())
            assertEquals(listOf(StartRadio.NO_PROFILE), messages.shown)
        }

    @Test
    fun `a seed that left the library plays nothing and stays quiet`() =
        runTest {
            radio()(SongId(999))

            assertTrue(playback.requests.isEmpty())
            assertTrue(messages.shown.isEmpty())
        }
}
