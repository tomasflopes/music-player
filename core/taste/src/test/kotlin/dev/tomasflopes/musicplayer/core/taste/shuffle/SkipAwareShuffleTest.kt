package dev.tomasflopes.musicplayer.core.taste.shuffle

import dev.tomasflopes.musicplayer.core.model.ArtistId
import dev.tomasflopes.musicplayer.core.model.SongId
import dev.tomasflopes.musicplayer.core.model.SongRef
import dev.tomasflopes.musicplayer.core.taste.config.SkipAwareShuffleConfig
import dev.tomasflopes.musicplayer.core.taste.lists.FakeScoreBoard
import dev.tomasflopes.musicplayer.core.taste.score.SongScore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

/** Every shuffle contract holds when nothing has a score: the strategy is then smart shuffle. */
class SkipAwareShuffleUnscoredTest : ShuffleStrategyContract() {
    override fun strategy(): ShuffleStrategy = SkipAwareShuffle({ emptyMap() })
}

/** ...and when one song in ten has a negative score. */
class SkipAwareShuffleSkippedTest : ShuffleStrategyContract() {
    override fun strategy(): ShuffleStrategy =
        SkipAwareShuffle({ (0L until 16_000L step 10).associate { SongId(it) to -3.0 } })
}

class SkipAwareShuffleTest {
    private fun refs(count: Int) = (0 until count).map { SongRef(SongId(it.toLong()), ArtistId((it % 40).toLong())) }

    private fun scored(vararg pairs: Pair<Long, Double>) =
        GlobalScores {
            pairs.associate {
                SongId(it.first) to
                    it.second
            }
        }

    @Test
    fun `without negative scores the order is smart shuffle's, seed for seed`() {
        val items = refs(300)
        val positives = scored(1L to 5.0, 2L to 0.0)

        assertEquals(SmartShuffle().order(items, Random(9)), SkipAwareShuffle(positives).order(items, Random(9)))
        assertEquals(SmartShuffle().order(items, Random(9)), SkipAwareShuffle({ emptyMap() }).order(items, Random(9)))
    }

    @Test
    fun `songs with no score or a positive score keep the order smart shuffle gives them`() {
        val items = refs(300)
        val negative = (0L until 300L step 7).toSet()
        val shuffle = SkipAwareShuffle(GlobalScores { negative.associate { SongId(it) to -2.0 } })

        val result = shuffle.order(items, Random(4)).filter { it.songId.value !in negative }

        assertEquals(SmartShuffle().order(items.filter { it.songId.value !in negative }, Random(4)), result)
    }

    @Test
    fun `every song still plays, skipped ones included`() {
        val items = refs(500)
        val shuffle = SkipAwareShuffle(GlobalScores { items.associate { it.songId to -10.0 } })

        val order = shuffle.order(items, Random(1))

        assertEquals(items.toSet(), order.toSet())
        assertEquals(items.size, order.size)
    }

    @Test
    fun `a skipped song comes later on average, and a more skipped one later still`() {
        val items = refs(200)
        val shuffle = SkipAwareShuffle(scored(0L to -2.0, 1L to -20.0))
        val seeds = 400
        val positions =
            (1..seeds).map { seed ->
                val order = shuffle.order(items, Random(seed)).map { it.songId.value }
                Triple(order.indexOf(0L), order.indexOf(1L), order.indexOf(2L))
            }

        val mild = positions.map { it.first }.average()
        val heavy = positions.map { it.second }.average()
        val neutral = positions.map { it.third }.average()

        assertTrue(neutral < 115 && neutral > 85, "neutral song averages place $neutral of 200")
        assertTrue(mild > neutral + 15, "mild $mild vs neutral $neutral")
        assertTrue(heavy > mild + 15, "heavy $heavy vs mild $mild")
    }

    @Test
    fun `a skipped song keeps clear of its artist's songs while neutral songs are left to place around it`() {
        val items = refs(400)
        val shuffle = SkipAwareShuffle(scored(0L to -2.0, 41L to -2.0, 82L to -2.0))
        for (seed in 1..40) {
            val order = shuffle.order(items, Random(seed))
            listOf(0L, 41L, 82L).forEach { id ->
                val at = order.indexOfFirst { it.songId.value == id }
                if (at > order.size - 30) return@forEach // the tail has nothing left to place around
                val artist = order[at].mainArtistId
                val near =
                    order.subList(maxOf(0, at - 4), minOf(order.size, at + 5)).filterIndexed { i, _ ->
                        maxOf(
                            0,
                            at - 4,
                        ) +
                            i !=
                            at
                    }
                assertTrue(near.none { it.mainArtistId == artist }, "seed $seed song $id")
            }
        }
    }

    @Test
    fun `songs that play before the shuffled ones count as neighbours`() {
        val items = refs(100)
        val shuffle = SkipAwareShuffle(scored(0L to -1.0))
        val preceding = listOf(SongRef(SongId(1_000), items[0].mainArtistId))
        for (seed in 1..40) {
            val order = shuffle.orderAfter(preceding, items, Random(seed))

            assertTrue(
                order.take(4).none { it.songId.value == 0L || it.mainArtistId == preceding[0].mainArtistId },
                "seed $seed",
            )
        }
    }

    @Test
    fun `it does not change the input`() {
        val items = refs(100)
        val copy = items.toList()

        SkipAwareShuffle(scored(3L to -4.0)).order(items, Random(2))

        assertEquals(copy, items)
    }

    @Test
    fun `the weight is 1 from a score of 0 up and falls with the score, to a floor`() {
        val config = SkipAwareShuffleConfig()

        assertEquals(1.0, config.weightOf(0.0))
        assertEquals(1.0, config.weightOf(7.0))
        assertEquals(0.5, config.weightOf(-2.0), 1e-12)
        assertTrue(config.weightOf(-4.0) < config.weightOf(-2.0))
        assertEquals(config.minWeight, config.weightOf(-10_000.0))
    }

    @Test
    fun `the config refuses nonsense`() {
        assertThrows(IllegalArgumentException::class.java) { SkipAwareShuffleConfig(penaltyPerPoint = -1.0) }
        assertThrows(IllegalArgumentException::class.java) { SkipAwareShuffleConfig(penaltyPerPoint = Double.NaN) }
        assertThrows(IllegalArgumentException::class.java) { SkipAwareShuffleConfig(minWeight = 0.0) }
        assertThrows(IllegalArgumentException::class.java) { SkipAwareShuffleConfig(minWeight = 1.5) }
    }

    @Test
    fun `the board's latest ranking is what the shuffle reads, and unscored until it arrives`() =
        runTest {
            val board = FakeScoreBoard(listOf(SongScore(SongId(5), -3.0, 0L), SongScore(SongId(6), 2.0, 0L)))

            val scores = BoardGlobalScores(board, CoroutineScope(UnconfinedTestDispatcher(testScheduler) + Job()))

            assertEquals(mapOf(SongId(5) to -3.0, SongId(6) to 2.0), scores.snapshot())
            assertEquals(
                emptyMap<SongId, Double>(),
                BoardGlobalScores(
                    FakeScoreBoard(emptyList()),
                    CoroutineScope(
                        Dispatchers.Unconfined + Job(),
                    ),
                ).snapshot(),
            )
        }
}
