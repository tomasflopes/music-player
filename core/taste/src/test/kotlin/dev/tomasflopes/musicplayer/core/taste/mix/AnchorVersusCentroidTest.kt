package dev.tomasflopes.musicplayer.core.taste.mix

import dev.tomasflopes.musicplayer.core.model.sound.SoundSpace
import dev.tomasflopes.musicplayer.core.taste.config.MixConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.cos
import kotlin.math.sin

/**
 * The first task of slice 1 (plan, ADR 0076): both rules on a space with three clusters. A and B are two sounds the
 * seeds come from; D is a bridge of songs that sound like neither, halfway between them. The centroid of one seed from
 * each of A and B lands inside D, where no seed sound lives; the anchors stay in A and B.
 */
class AnchorVersusCentroidTest {
    private val rows = ArrayList<Triple<Long, Float, Float>>()

    /** A grid of [cols] x [rows] songs around ([cx], [cy]), ids from [firstId]; returns the id of the middle song. */
    private fun grid(
        firstId: Long,
        cx: Float,
        cy: Float,
        cols: Int,
        rowCount: Int,
        step: Float,
    ): Long {
        var id = firstId
        for (r in 0 until rowCount) {
            for (c in 0 until cols) {
                rows += Triple(id++, cx + (c - (cols - 1) / 2f) * step, cy + (r - (rowCount - 1) / 2f) * step)
            }
        }
        return firstId + (cols / 2) + (rowCount / 2) * cols
    }

    private val seedA = grid(1, 0f, 0f, 9, 5, 0.2f)
    private val seedB = grid(101, 10f, 0f, 9, 5, 0.2f)

    init {
        // D: 30 songs on a ring of radius 1 around (5, 0), ids 201..230.
        (0 until 30).forEach { i ->
            val angle = 2.0 * Math.PI * i / 30
            rows += Triple(201L + i, (5 + cos(angle)).toFloat(), sin(angle).toFloat())
        }
    }

    private val space =
        SoundSpace(
            rows.map { it.first }.toLongArray(),
            2,
            rows.flatMap { listOf(it.second, it.third) }.toFloatArray(),
        )

    private val config = MixConfig()

    private fun anchorOf(songId: Long): DoubleArray {
        val row = space.indexOf(songId)
        return doubleArrayOf(space.values[row * 2].toDouble(), space.values[row * 2 + 1].toDouble())
    }

    private val anchors = listOf(anchorOf(seedA), anchorOf(seedB))

    private fun top25(query: MixQuery): List<Long> {
        val ranking = notNull(query.ranking(space, anchors))
        return (0 until space.size)
            .sortedWith(compareBy<Int> { ranking.keyOf(it) }.thenBy { space.songIds[it] })
            .take(25)
            .map { space.songIds[it] }
    }

    private fun cluster(id: Long) =
        when (id) {
            in 1L..45L -> "A"
            in 101L..145L -> "B"
            else -> "D"
        }

    @Test
    fun `the centroid of one song from each cluster lands between them`() {
        val centre = space.centroid(listOf(space.indexOf(seedA), space.indexOf(seedB)))

        assertEquals(5.0, centre[0], 0.05)
        assertEquals(0.0, centre[1], 0.05)
        // No song of A or B is within 4 of it; the nearest songs are the bridge's.
        val nearest = space.nearestTo(centre, 1).single()
        assertEquals("D", cluster(nearest.songId))
        assertTrue(nearest.distance > 0.9)
    }

    @Test
    fun `anchors stay in the two seed clusters and the centroid is pulled into the bridge`() {
        val anchorTop = top25(AnchorQuery(config))
        val centroidTop = top25(CentroidQuery(config))

        assertTrue(anchorTop.all { cluster(it) != "D" }, "anchor top 25: ${anchorTop.map(::cluster)}")
        assertTrue(anchorTop.count { cluster(it) == "A" } >= 10)
        assertTrue(anchorTop.count { cluster(it) == "B" } >= 10)
        assertTrue(centroidTop.all { cluster(it) == "D" }, "centroid top 25: ${centroidTop.map(::cluster)}")
    }

    @Test
    fun `the two top 25 lists share no song`() {
        val overlap = top25(AnchorQuery(config)).toSet().intersect(top25(CentroidQuery(config)).toSet())

        assertEquals(0, overlap.size)
    }
}
