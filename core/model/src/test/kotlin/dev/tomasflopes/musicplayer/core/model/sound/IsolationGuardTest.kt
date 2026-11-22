package dev.tomasflopes.musicplayer.core.model.sound

import dev.tomasflopes.musicplayer.core.model.config.IsolationGuardConfig
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class IsolationGuardTest {
    // The 10th-nearest rule is the default; these tests use rank 2 so a small line is enough.
    private val guard = IsolationGuard(IsolationGuardConfig(rank = 2))

    /**
     * One dimension. Songs 1..20 (artist "other") sit at 10.0, 10.1 .. 11.9. The seed (100, artist "seed") sits alone
     * at 0. Own songs are farther than the cluster: 104 (artist "x", featuring "seed") at 30, 101, 102, 103 and 105
     * (artist "seed") at 31, 32, 33 and 34. Rank-2 distances: the cluster 0.2, the seed 10.1, the threshold 1.5.
     */
    private val ids = (1L..20L).toList() + listOf(100L, 101L, 102L, 103L, 104L, 105L)
    private val positions =
        (0..19).map { 10f + it * 0.1f } + listOf(0f, 31f, 32f, 33f, 30f, 34f)
    private val space = SoundSpace(ids.toLongArray(), 1, positions.toFloatArray())
    private val seedRow = space.indexOf(100)

    private val artists: Map<Long, SongArtistKeys> =
        ids.associateWith { SongArtistKeys(main = "other") } +
            mapOf(
                100L to SongArtistKeys("seed"),
                101L to SongArtistKeys("seed"),
                102L to SongArtistKeys("seed"),
                103L to SongArtistKeys("seed"),
                104L to SongArtistKeys("x", featured = setOf("seed")),
                105L to SongArtistKeys("seed"),
            )

    private fun blend(
        row: Int,
        k: Int = 10,
        artistsOfSong: (Long) -> SongArtistKeys? = { artists[it] },
        accept: (Int) -> Boolean = { true },
    ) = guard.blend(space, row, k, accept, artistsOfSong).map { it.songId }

    @Test
    fun `a seed inside a dense area is not isolated and its list is unchanged`() {
        val row = space.indexOf(5)

        assertFalse(guard.isIsolated(space, row))
        assertEquals(space.nearest(row, 10).map { it.songId }, blend(row))
    }

    @Test
    fun `a seed far from everything is isolated`() {
        assertTrue(guard.isIsolated(space, seedRow))
    }

    @Test
    fun `an isolated seed gets 3 of 10 slots from its own and featured artists, all ordered by distance`() {
        val found = blend(seedRow)

        // 7 nearest by sound (songs 1..7), then the 3 nearest own songs: 104 (featured), 101, 102.
        assertEquals(listOf(1L, 2, 3, 4, 5, 6, 7, 104, 101, 102), found)
    }

    @Test
    fun `fewer artist songs than slots are filled with sound neighbours`() {
        val onlyOne = { id: Long -> if (id == 104L || id == 100L) artists[id] else SongArtistKeys("other") }

        val found = blend(seedRow, artistsOfSong = onlyOne)

        assertEquals((1L..9L).toList() + 104L, found)
    }

    @Test
    fun `no artist songs at all leaves the sound list`() {
        val noOthers = { id: Long -> if (id == 100L) artists[id] else SongArtistKeys("other") }

        assertEquals(space.nearest(seedRow, 10).map { it.songId }, blend(seedRow, artistsOfSong = noOthers))
    }

    @Test
    fun `songs the caller refuses are not used as artist songs`() {
        val refused = space.indexOf(104)

        val found = blend(seedRow, accept = { it != refused })

        assertEquals(listOf(1L, 2, 3, 4, 5, 6, 7, 101, 102, 103), found)
    }

    @Test
    fun `a seed with no known artist leaves the sound list`() {
        assertEquals(space.nearest(seedRow, 10).map { it.songId }, blend(seedRow, artistsOfSong = { null }))
    }

    @Test
    fun `a seed with fewer neighbours than the rank is never isolated`() {
        val tiny = SoundSpace(longArrayOf(1, 2), 1, floatArrayOf(0f, 100f))

        assertFalse(IsolationGuard(IsolationGuardConfig(rank = 5)).isIsolated(tiny, 0))
    }

    @Test
    fun `the sample is evenly spaced rows and the whole space when it is small`() {
        assertArrayEquals(intArrayOf(0, 10, 20, 30, 40, 50, 60, 70, 80, 90), guard.sampleRows(100, 10))
        assertArrayEquals(intArrayOf(0, 1, 2, 3), guard.sampleRows(4, 1500))
        assertEquals(1500, guard.sampleRows(14_165, 1500).size)
        assertEquals(0, guard.sampleRows(14_165, 1500).first())
        assertTrue(guard.sampleRows(14_165, 1500).last() < 14_165)
    }

    @Test
    fun `the threshold is p90 of the rank distance, the same on every call and for an equal space`() {
        val again = SoundSpace(ids.toLongArray(), 1, positions.toFloatArray())

        assertEquals(1.5, guard.threshold(space), 1e-5)
        assertEquals(guard.threshold(space), guard.threshold(space), 0.0)
        assertEquals(guard.threshold(space), guard.threshold(again), 0.0)
    }

    @Test
    fun `the number of artist slots is the share of k rounded`() {
        val default = IsolationGuard()

        assertEquals(3, default.artistSlots(10))
        assertEquals(0, default.artistSlots(1))
        assertEquals(6, default.artistSlots(20))
    }

    @Test
    fun `the config rejects numbers that cannot work`() {
        assertThrows(IllegalArgumentException::class.java) { IsolationGuardConfig(rank = 0) }
        assertThrows(IllegalArgumentException::class.java) { IsolationGuardConfig(thresholdPercentile = 101.0) }
        assertThrows(IllegalArgumentException::class.java) { IsolationGuardConfig(sampleSize = 0) }
        assertThrows(IllegalArgumentException::class.java) { IsolationGuardConfig(artistShare = 1.5) }
        assertThrows(IllegalArgumentException::class.java) { IsolationGuardConfig(artistShare = Double.NaN) }
    }

    @Test
    fun `an artist key ignores case, accents encoding and outer spaces`() {
        assertEquals(SongArtistKeys.key("Yung Juse"), SongArtistKeys.key("  yung juse "))
        assertEquals(SongArtistKeys.key("Bal\u00e3o"), SongArtistKeys.key("Bala\u0303o"))
    }
}
