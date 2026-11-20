package dev.tomasflopes.musicplayer.refill

import dev.tomasflopes.musicplayer.core.model.log.PlaySource
import dev.tomasflopes.musicplayer.core.model.queue.RefillSeed
import dev.tomasflopes.musicplayer.core.taste.config.RadioConfig
import dev.tomasflopes.musicplayer.core.taste.config.SoloExtensionConfig
import dev.tomasflopes.musicplayer.core.taste.lists.RadioPolicy
import dev.tomasflopes.musicplayer.fakes.FakeSettingsRepository
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SoloRefillSourceTest {
    private val solo = SoloExtensionConfig()
    private val radio = RadioConfig(seed = 7)
    private val settings = FakeSettingsRepository()
    private val seed = RefillSeed.Song(1)

    private fun source(profiled: List<Long> = refillSongs.map { it.id.value }) =
        SoloRefillSource(
            settings,
            FixedVectors(spaceOf(profiled)),
            refillLibrary(),
            RadioPolicy(solo.radioConfig(radio)),
            solo,
            radio,
        )

    @Test
    fun `it handles song seeds only and refills at 2 or fewer left`() {
        val source = source()

        assertTrue(source.handles(RefillSeed.Song(1)))
        assertFalse(source.handles(RefillSeed.Mix(1)))
        assertEquals(2, source.lowWater)
    }

    @Test
    fun `it follows the setting at each call`() =
        runTest {
            val source = source()

            assertTrue(source.enabled())
            settings.setExtendSingleSongs(false)
            assertFalse(source.enabled())
        }

    @Test
    fun `a batch has 5 songs that are not the seed and not queued, logged as radio`() =
        runTest {
            val queued = refs(1, 2, 3)

            val batch = source().batch(signal(seed, queued))

            val ids = batch.songs.map { it.id.value }
            assertEquals(5, ids.size)
            assertEquals(5, ids.toSet().size)
            assertTrue(ids.none { it in 1L..3L })
            assertEquals(PlaySource.RADIO, batch.source)
        }

    @Test
    fun `the nearest songs come first and the anchor stays the chosen song`() =
        runTest {
            val first = source().batch(signal(seed, refs(1))).songs.map { it.id.value }
            val later = source().batch(signal(seed, refs(1, 2, 3, 4, 5))).songs.map { it.id.value }

            assertEquals(listOf(2L, 3L, 4L, 5L), first.take(4))
            assertEquals(listOf(6L, 7L, 8L, 9L), later.take(4))
        }

    @Test
    fun `a seed without a sound profile gives nothing`() =
        runTest {
            val batch = source(profiled = (2L..60L).toList()).batch(signal(seed, refs(1)))

            assertTrue(batch.songs.isEmpty())
        }

    @Test
    fun `a seed that left the library gives nothing`() =
        runTest {
            val batch = source().batch(signal(RefillSeed.Song(999), refs(999)))

            assertTrue(batch.songs.isEmpty())
        }
}
