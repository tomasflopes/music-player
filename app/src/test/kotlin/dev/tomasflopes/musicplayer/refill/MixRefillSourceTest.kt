package dev.tomasflopes.musicplayer.refill

import dev.tomasflopes.musicplayer.core.model.log.PlaySource
import dev.tomasflopes.musicplayer.core.model.mixes.Mix
import dev.tomasflopes.musicplayer.core.model.mixes.MixId
import dev.tomasflopes.musicplayer.core.model.mixes.MixSeed
import dev.tomasflopes.musicplayer.core.model.mixes.MixSeedKind
import dev.tomasflopes.musicplayer.core.model.queue.RefillSeed
import dev.tomasflopes.musicplayer.core.taste.config.MixConfig
import dev.tomasflopes.musicplayer.core.taste.mix.MixEngine
import dev.tomasflopes.musicplayer.core.taste.mix.MixLearning
import dev.tomasflopes.musicplayer.fakes.FakeMixScoreBoard
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class MixRefillSourceTest {
    private val id = MixId(3)
    private val config =
        MixConfig(seed = 7, explorationShare = 0.0, artistGap = 0, densityRank = 3, openingPerArtist = 0)
    private val seed = RefillSeed.Mix(3)

    private fun mix(vararg songIds: Long) = Mix(id, "Run", songIds.map { MixSeed(MixSeedKind.SONG, it) }, 1, 1)

    private val board = FakeMixScoreBoard()

    private fun source(mixes: FakeMixReader) =
        MixRefillSource(mixes, FixedVectors(spaceOf()), refillLibrary(), board, MixEngine(config), config)

    @Test
    fun `it handles mix seeds only and refills at 5 or fewer left`() {
        val source = source(FakeMixReader(mix(1)))

        assertTrue(source.handles(RefillSeed.Mix(1)))
        assertTrue(!source.handles(RefillSeed.Song(1)))
        assertEquals(5, source.lowWater)
    }

    @Test
    fun `a batch has 25 songs, none of them queued, logged as mix`() =
        runTest {
            val queued = refs(*(1L..25L).toList().toLongArray())

            val batch = source(FakeMixReader(mix(1))).batch(signal(seed, queued))

            assertEquals(25, batch.songs.size)
            assertTrue(batch.songs.none { it.id.value in 1L..25L })
            assertEquals(PlaySource.MIX, batch.source)
        }

    @Test
    fun `a deleted mix gives nothing`() =
        runTest {
            val batch = source(FakeMixReader(null)).batch(signal(seed, refs(1)))

            assertTrue(batch.songs.isEmpty())
        }

    @Test
    fun `an edited mix is read again at the next refill`() =
        runTest {
            val mixes = FakeMixReader(mix(1))
            val source = source(mixes)
            val before = source.batch(signal(seed, refs(1))).songs.map { it.id.value }

            mixes.current.value = mix(60)
            val after = source.batch(signal(seed, refs(60))).songs.map { it.id.value }

            assertEquals(2L, before.first())
            assertEquals(59L, after.first())
        }

    @Test
    fun `a mix whose seeds have no profile gives nothing`() =
        runTest {
            val vectors = FixedVectors(spaceOf(listOf(5, 6, 7)))
            val source =
                MixRefillSource(FakeMixReader(mix(1)), vectors, refillLibrary(), board, MixEngine(config), config)

            assertTrue(source.batch(signal(seed, refs(1))).songs.isEmpty())
        }

    @Test
    fun `it is always enabled`() =
        runTest {
            assertTrue(source(FakeMixReader(mix(1))).enabled())
        }

    @Test
    fun `asks the score board about this mix and the current time`() =
        runTest {
            source(FakeMixReader(mix(1))).batch(signal(seed, refs(1)))

            val now = Instant.parse("2026-11-20T12:00:00Z").toEpochMilli()
            assertEquals(listOf(id to now), board.asked)
        }

    @Test
    fun `banned songs are left out of the batch`() =
        runTest {
            val source = source(FakeMixReader(mix(1)))
            val plain = source.batch(signal(seed, refs(1))).songs.map { it.id }
            board.learning = MixLearning(banned = plain.take(3).toSet())

            val batch = source.batch(signal(seed, refs(1))).songs.map { it.id }

            assertEquals(25, batch.size)
            assertTrue(batch.none { it in plain.take(3) })
        }
}
