package dev.tomasflopes.musicplayer.core.taste.lists

import dev.tomasflopes.musicplayer.core.model.ArtistId
import dev.tomasflopes.musicplayer.core.model.Song
import dev.tomasflopes.musicplayer.core.model.SongId
import dev.tomasflopes.musicplayer.core.model.log.EndedBy
import dev.tomasflopes.musicplayer.core.model.log.PlayEvent
import dev.tomasflopes.musicplayer.core.model.log.PlaySource
import dev.tomasflopes.musicplayer.core.model.log.StartedBy
import dev.tomasflopes.musicplayer.core.taste.config.RewindConfig
import dev.tomasflopes.musicplayer.core.taste.score.TasteSignals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

class RewindPolicyTest {
    private val day = 86_400_000L
    private val now = 1_000 * day
    private val cutoff = now - 60 * day

    private fun by(
        id: Long,
        artist: Long,
    ): Song = song(id, 0).copy(artistId = ArtistId(artist))

    /** Songs 1..[count], each by an artist of its own (artist id = song id). */
    private fun soloists(count: Int) = (1..count).map { by(it.toLong(), it.toLong()) }

    /** A picked play of [song] that went to the end, outside shuffle: +3 on its own, [daysBeforeCutoff] old. */
    private fun lovedThen(
        song: Long,
        daysBeforeCutoff: Long = 1,
    ) = play(song, cutoff - daysBeforeCutoff * day)

    private fun play(
        song: Long,
        at: Long,
    ) = PlayEvent(SongId(song), at, 200_000, 200_000, EndedBy.COMPLETED, PlaySource.ALBUM, StartedBy.PICKED, false)

    private fun pick(
        library: List<Song>,
        plays: List<PlayEvent>,
        lately: Set<Long> = emptySet(),
        config: RewindConfig = RewindConfig(),
        seed: Int = 1,
    ): List<Song> =
        RewindPolicy(config).pick(
            library,
            lately.mapTo(HashSet()) { SongId(it) },
            TasteSignals(plays = plays),
            now,
            Random(seed),
        )

    private fun List<Song>.ids() = map { it.id.value }

    @Test
    fun `the cutoff is the quiet window before now and the logs are read from a horizon before it`() {
        val policy = RewindPolicy()

        assertEquals(cutoff, policy.cutoffAt(now))
        assertEquals(cutoff - 240 * day, policy.logsFrom(now))
        assertEquals(now - 7 * day, RewindPolicy(RewindConfig(quietDays = 7)).cutoffAt(now))
    }

    @Test
    fun `a song loved before the cutoff and not played since is listed`() {
        assertEquals(listOf(1L), pick(soloists(3), listOf(lovedThen(1))).ids())
    }

    @Test
    fun `a song with a play since the cutoff is left out whatever it scored`() {
        val plays = listOf(lovedThen(1), lovedThen(2), play(2, cutoff + day))

        assertEquals(listOf(1L), pick(soloists(3), plays, lately = setOf(2)).ids())
    }

    @Test
    fun `only the score at the cutoff counts, not what came after`() {
        val afterCutoff = (1..5).map { play(2, cutoff + it * day) }
        val plays = listOf(lovedThen(1)) + afterCutoff

        assertEquals(listOf(1L), pick(soloists(3), plays).ids())
    }

    @Test
    fun `the minimum is on the score then, which decays up to the cutoff`() {
        val config = RewindConfig(minScoreThen = 2.0)
        // 3 * 0.5^(10/30) = 2.38 is loved; 3 * 0.5^(40/30) = 1.19 is not.
        val plays = listOf(lovedThen(1, daysBeforeCutoff = 10), lovedThen(2, daysBeforeCutoff = 40))

        val lenient = RewindConfig(minScoreThen = 1.0)

        assertEquals(listOf(1L), pick(soloists(3), plays, config = config).ids())
        assertEquals(listOf(1L, 2L), pick(soloists(3), plays, config = lenient).ids().sorted())
    }

    @Test
    fun `a song skipped a lot before the cutoff is not loved`() {
        val skip = lovedThen(1).copy(listenedMs = 5_000, endedBy = EndedBy.SKIPPED, startedBy = StartedBy.NAV)

        val twice = listOf(skip, skip.copy(startedAtEpochMs = cutoff - 2 * day))

        assertEquals(emptyList<Long>(), pick(soloists(2), twice))
    }

    @Test
    fun `best past score first`() {
        val noRandom = RewindConfig(randomWeight = 0.0)
        val plays =
            listOf(lovedThen(1, 20), lovedThen(1, 21)) +
                listOf(lovedThen(2, 1), lovedThen(2, 2)) +
                listOf(lovedThen(3, 5), lovedThen(3, 6))

        assertEquals(listOf(2L, 3L, 1L), pick(soloists(3), plays, config = noRandom).ids())
    }

    @Test
    fun `one artist cannot have more than the cap and the next artist takes the room`() {
        val library = (1L..5L).map { by(it, artist = 1) } + by(6, artist = 2)
        val plays = (1L..6L).map { lovedThen(it) }
        val config = RewindConfig(randomWeight = 0.0, artistGap = 0, maxPerArtist = 2)

        val picked = pick(library, plays, config = config)

        assertEquals(3, picked.size)
        assertEquals(2, picked.count { it.artistId == ArtistId(1) })
        assertTrue(by(6, 2) in picked)
    }

    @Test
    fun `the cap is hard, so a one-artist library gives a short list`() {
        val library = (1L..10L).map { by(it, artist = 1) }

        assertEquals(3, pick(library, library.map { lovedThen(it.id.value) }).size)
    }

    @Test
    fun `the list is never longer than the size`() {
        val plays = (1L..200L).map { lovedThen(it) }

        assertEquals(50, pick(soloists(200), plays).size)
        assertEquals(10, pick(soloists(200), plays, config = RewindConfig(size = 10)).size)
    }

    @Test
    fun `fewer candidates than the list size gives all of them`() {
        val picked = pick(soloists(10), (1L..4L).map { lovedThen(it) })

        assertEquals(listOf(1L, 2L, 3L, 4L), picked.ids().sorted())
    }

    @Test
    fun `an empty log gives an empty list`() {
        assertEquals(emptyList<Song>(), pick(soloists(5), emptyList()))
    }

    @Test
    fun `an empty library gives an empty list`() {
        assertEquals(emptyList<Song>(), pick(emptyList(), listOf(lovedThen(1))))
    }

    @Test
    fun `a song that left the library is skipped`() {
        assertEquals(listOf(1L), pick(soloists(2), listOf(lovedThen(1), lovedThen(99))).ids())
    }

    @Test
    fun `the same seed gives the same list and another seed another one`() {
        val plays = (1L..200L).map { lovedThen(it) }

        assertEquals(pick(soloists(200), plays, seed = 7), pick(soloists(200), plays, seed = 7))
        assertNotEquals(pick(soloists(200), plays, seed = 7).ids(), pick(soloists(200), plays, seed = 8).ids())
    }

    @Test
    fun `the list does not depend on the order the library comes in`() {
        val plays = (1L..100L).map { lovedThen(it) }

        assertEquals(pick(soloists(100), plays).ids(), pick(soloists(100).reversed(), plays).ids())
    }

    @Test
    fun `a song that gets played leaves and the others keep their order`() {
        val plays = (1L..30L).map { lovedThen(it) }
        val before = pick(soloists(30), plays)

        val after = pick(soloists(30), plays, lately = setOf(before.first().id.value))

        assertEquals(before.drop(1), after)
    }

    @Test
    fun `no artist repeats inside the gap when the list can avoid it`() {
        val library = (0L until 10L).flatMap { artist -> (1L..3L).map { by(artist * 3 + it, artist) } }
        val noRandom = RewindConfig(randomWeight = 0.0)

        val artists = pick(library, library.map { lovedThen(it.id.value) }, config = noRandom).map { it.artistId }

        assertEquals(30, artists.size)
        artists.forEachIndexed { index, artist ->
            assertTrue(artist !in artists.drop(index + 1).take(noRandom.artistGap), "artist $artist repeats at $index")
        }
    }

    @Test
    fun `the gap relaxes when there are too few artists`() {
        val library = (1L..3L).map { by(it, artist = 1) } + (4L..6L).map { by(it, artist = 2) }

        val picked = pick(library, library.map { lovedThen(it.id.value) }, config = RewindConfig(randomWeight = 0.0))

        assertEquals(listOf(1L, 4L, 2L, 5L, 3L, 6L), picked.ids())
    }
}
