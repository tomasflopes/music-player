package dev.tomasflopes.musicplayer.core.taste.mix

import dev.tomasflopes.musicplayer.core.model.sound.SoundSpace
import dev.tomasflopes.musicplayer.core.taste.config.MixConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** What every [MixQuery] must do, whatever its rule. Each implementation has a subclass. */
abstract class MixQueryContract {
    abstract fun query(config: MixConfig): MixQuery

    /** Songs 1..[count] at position = id on one axis. */
    protected fun ramp(count: Int) = SoundSpace(LongArray(count) { it + 1L }, 1, FloatArray(count) { it + 1f })

    private val config = MixConfig(densityRank = 3)

    @Test
    fun `no anchors give no ranking`() {
        assertNull(query(config).ranking(ramp(10), emptyList()))
    }

    @Test
    fun `a song nearer the anchor never ranks after a farther one`() {
        val space = ramp(20)
        val ranking = notNull(query(config).ranking(space, listOf(doubleArrayOf(5.0))))

        val keys = (0 until space.size).map { ranking.keyOf(it) }

        // Songs 5, 6, 7, 8 are 0, 1, 2, 3 away: the keys must not fall as the distance grows.
        assertTrue(keys[4] <= keys[5] && keys[5] <= keys[6] && keys[6] <= keys[7])
        assertTrue(keys[4] < keys[19])
    }

    @Test
    fun `keys are finite and not negative`() {
        val space = ramp(20)
        val ranking = notNull(query(config).ranking(space, listOf(doubleArrayOf(2.0), doubleArrayOf(15.0))))

        (0 until space.size).forEach { row ->
            val key = ranking.keyOf(row)
            assertTrue(key.isFinite() && key >= 0.0, "row $row has key $key")
        }
    }

    @Test
    fun `the same input gives the same keys`() {
        val space = ramp(20)
        val anchors = listOf(doubleArrayOf(4.0), doubleArrayOf(12.0))

        val first = notNull(query(config).ranking(space, anchors))
        val second = notNull(query(config).ranking(space, anchors))

        (0 until space.size).forEach { assertEquals(first.keyOf(it), second.keyOf(it)) }
    }

    @Test
    fun `a space of identical points does not divide by zero`() {
        val space = SoundSpace(longArrayOf(1, 2, 3), 1, floatArrayOf(1f, 1f, 1f))
        val ranking = notNull(query(config).ranking(space, listOf(doubleArrayOf(1.0))))

        (0 until 3).forEach { assertTrue(ranking.keyOf(it).isFinite()) }
    }
}
