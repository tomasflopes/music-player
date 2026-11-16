package dev.tomasflopes.musicplayer.core.taste.mix

import dev.tomasflopes.musicplayer.core.taste.config.MixConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CentroidQueryTest : MixQueryContract() {
    override fun query(config: MixConfig): MixQuery = CentroidQuery(config)

    @Test
    fun `the query point is the mean of the anchors`() {
        val space = ramp(21)
        val anchors = listOf(doubleArrayOf(5.0), doubleArrayOf(15.0))
        val ranking = notNull(CentroidQuery(MixConfig(densityRank = 3)).ranking(space, anchors))

        // The mean is 10: song 10 is on it, songs 5 and 15 are equally far.
        assertEquals(0.0, ranking.keyOf(9), 0.0)
        assertEquals(ranking.keyOf(4), ranking.keyOf(14), 1e-9)
    }
}
