package dev.tomasflopes.musicplayer.mix

import dev.tomasflopes.musicplayer.core.model.SongId
import dev.tomasflopes.musicplayer.core.model.log.PlaySource
import dev.tomasflopes.musicplayer.core.model.mixes.Mix
import dev.tomasflopes.musicplayer.core.model.mixes.MixId
import dev.tomasflopes.musicplayer.core.model.mixes.MixReader
import dev.tomasflopes.musicplayer.core.model.mixes.MixSeed
import dev.tomasflopes.musicplayer.core.model.mixes.MixSeedKind
import dev.tomasflopes.musicplayer.core.model.ports.PlayedSinceSource
import dev.tomasflopes.musicplayer.core.model.ports.SoundVectorSource
import dev.tomasflopes.musicplayer.core.model.sound.SoundSpace
import dev.tomasflopes.musicplayer.core.taste.config.MixConfig
import dev.tomasflopes.musicplayer.core.taste.mix.MixEngine
import dev.tomasflopes.musicplayer.core.taste.mix.MixLearning
import dev.tomasflopes.musicplayer.core.taste.shuffle.BatchOrdering
import dev.tomasflopes.musicplayer.fakes.FakeMixScoreBoard
import dev.tomasflopes.musicplayer.fakes.FakePlaybackController
import dev.tomasflopes.musicplayer.fakes.FakeSongReader
import dev.tomasflopes.musicplayer.fakes.testSong
import dev.tomasflopes.musicplayer.radio.ShortMessages
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class StartMixTest {
    private class FakeMixes(
        private val mix: Mix?,
    ) : MixReader {
        override fun observeMixes(): Flow<List<Mix>> = flowOf(listOfNotNull(mix))

        override fun observeMix(id: MixId): Flow<Mix?> = flowOf(mix)

        override suspend fun mix(id: MixId): Mix? = mix?.takeIf { it.id == id }
    }

    private class FakeVectors(
        private val space: SoundSpace,
    ) : SoundVectorSource {
        override suspend fun space(): SoundSpace = space
    }

    private class FakePlayedSince : PlayedSinceSource {
        val asked = mutableListOf<Long>()

        override fun observePlayedIdsSince(sinceEpochMs: Long): Flow<Set<SongId>> {
            asked += sinceEpochMs
            return flowOf(emptySet())
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
    private val played = FakePlayedSince()
    private val board = FakeMixScoreBoard()
    private val now = Instant.parse("2026-11-16T12:00:00Z")
    private val config = MixConfig(seed = 7)
    private val songs = (1L..60L).map { testSong(it, artistId = it, title = "Title $it", artistName = "Artist $it") }
    private val id = MixId(3)

    private fun seeds(vararg songIds: Long) = songIds.map { MixSeed(MixSeedKind.SONG, it) }

    private fun start(
        seeds: List<MixSeed>,
        profiled: List<Long> = songs.map { it.id.value },
        exists: Boolean = true,
        learning: MixLearning = MixLearning.EMPTY,
    ) = StartMix(
        mixes = FakeMixes(if (exists) Mix(id, "Run", seeds, 1, 1) else null),
        songs = FakeSongReader(songs = songs),
        vectors = FakeVectors(SoundSpace(profiled.toLongArray(), 1, profiled.map { it.toFloat() }.toFloatArray())),
        played = played,
        scores = board.also { it.learning = learning },
        playback = playback,
        messages = messages,
        engine = MixEngine(config, ordering = BatchOrdering.Identity),
        config = config,
        clock = Clock.fixed(now, ZoneOffset.UTC),
    )

    @Test
    fun `plays one batch of 25 as a mix request that carries the mix id`() =
        runTest {
            start(seeds(5, 30, 50))(id)

            val request = playback.requests.single()
            assertEquals(0, request.startIndex)
            assertEquals(false, request.shuffle)
            assertEquals(PlaySource.MIX, request.source)
            assertEquals(true, request.picked)
            assertEquals(3L, request.mixId)
            assertEquals(null, request.playlistId)
            assertEquals(25, request.songs.size)
            assertTrue(messages.shown.isEmpty())
        }

    @Test
    fun `asks the log about the last two days`() =
        runTest {
            start(seeds(5, 30, 50))(id)

            assertEquals(listOf(now.toEpochMilli() - 2 * 86_400_000L), played.asked)
        }

    @Test
    fun `a mix whose seeds have no sound profile plays nothing and says so`() =
        runTest {
            start(seeds(5, 30, 50), profiled = (1L..60L).filter { it !in setOf(5L, 30L, 50L) })(id)

            assertTrue(playback.requests.isEmpty())
            assertEquals(listOf(StartMix.NO_PROFILE), messages.shown)
        }

    @Test
    fun `one profiled seed is enough`() =
        runTest {
            start(seeds(5, 30, 50), profiled = (1L..60L).filter { it != 30L && it != 50L })(id)

            assertEquals(1, playback.requests.size)
            assertTrue(messages.shown.isEmpty())
        }

    @Test
    fun `a mix that no longer exists does nothing`() =
        runTest {
            start(seeds(5), exists = false)(id)

            assertTrue(playback.requests.isEmpty())
            assertTrue(messages.shown.isEmpty())
        }

    @Test
    fun `asks the score board about this mix and the current time`() =
        runTest {
            start(seeds(5, 30, 50))(id)

            assertEquals(listOf(id to now.toEpochMilli()), board.asked)
        }

    @Test
    fun `an empty learning gives the same batch as before learning existed`() =
        runTest {
            start(seeds(5, 30, 50))(id)
            start(seeds(5, 30, 50), learning = MixLearning())(id)

            val (first, second) = playback.requests.map { request -> request.songs.map { it.songId } }
            assertEquals(first, second)
        }

    @Test
    fun `banned songs are not played, the opening ones included`() =
        runTest {
            start(seeds(5, 30, 50))(id)
            val before =
                playback.requests
                    .single()
                    .songs
                    .map { it.songId }
            val banned = setOf(before[0], before[1], before[2])
            playback.requests.clear()

            start(seeds(5, 30, 50), learning = MixLearning(banned = banned))(id)

            val after =
                playback.requests
                    .single()
                    .songs
                    .map { it.songId }
            assertEquals(25, after.size)
            assertTrue(after.none { it in banned })
        }
}
