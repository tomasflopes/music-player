package dev.tomasflopes.musicplayer.core.taste.mix

import dev.tomasflopes.musicplayer.core.model.sound.SoundSpace
import dev.tomasflopes.musicplayer.core.taste.config.MixConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AnchorQueryTest : MixQueryContract() {
    override fun query(config: MixConfig): MixQuery = AnchorQuery(config)

    @Test
    fun `a song on an anchor has the lowest key`() {
        val space = ramp(20)
        val anchors = listOf(doubleArrayOf(5.0))
        val ranking = notNull(AnchorQuery(MixConfig(densityRank = 3)).ranking(space, anchors))

        assertEquals(0.0, ranking.keyOf(4), 0.0)
    }

    @Test
    fun `each anchor pulls its own neighbourhood`() {
        val space = ramp(40)
        val anchors = listOf(doubleArrayOf(5.0), doubleArrayOf(35.0))
        val ranking = notNull(AnchorQuery(MixConfig(densityRank = 3)).ranking(space, anchors))

        // Song 20 is far from both anchors; songs 6 and 34 sit beside one each.
        assertTrue(ranking.keyOf(5) < ranking.keyOf(19))
        assertTrue(ranking.keyOf(33) < ranking.keyOf(19))
    }

    @Test
    fun `a crowded anchor does not outrank a sparse one at the same distance`() {
        // Anchor at 0 has songs 1 apart, anchor at 100 has songs 10 apart. Songs 3 and 130 are 3 and 30 away,
        // which is the same number of neighbours out for each, so their keys match.
        val ids = LongArray(14) { it + 1L }
        val points = floatArrayOf(0f, 1f, 2f, 3f, 4f, 5f, 6f, 100f, 110f, 120f, 130f, 140f, 150f, 160f)
        val space = SoundSpace(ids, 1, points)
        val anchors = listOf(doubleArrayOf(0.0), doubleArrayOf(100.0))
        val ranking = notNull(AnchorQuery(MixConfig(densityRank = 4)).ranking(space, anchors))

        assertEquals(ranking.keyOf(3), ranking.keyOf(10), 1e-9)
    }
}
