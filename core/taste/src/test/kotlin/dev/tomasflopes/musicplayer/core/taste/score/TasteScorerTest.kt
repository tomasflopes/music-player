package dev.tomasflopes.musicplayer.core.taste.score

import dev.tomasflopes.musicplayer.core.model.SongId
import dev.tomasflopes.musicplayer.core.model.log.EndedBy
import dev.tomasflopes.musicplayer.core.model.log.PlayEvent
import dev.tomasflopes.musicplayer.core.model.log.PlaySource
import dev.tomasflopes.musicplayer.core.model.log.StartedBy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TasteScorerTest {
    private val scorer = TasteScorer()
    private val day = 86_400_000L
    private val now = 1_000 * day

    /** A play of [song] that went to the end under shuffle: worth +1. */
    private fun completed(
        song: Long,
        at: Long,
    ) = PlayEvent(SongId(song), at, 200_000, 200_000, EndedBy.COMPLETED, PlaySource.SHUFFLE_ALL, StartedBy.AUTO, true)

    /** A skip after 5 s: worth -2 when it is on its own. */
    private fun quickSkip(
        song: Long,
        at: Long,
    ) = PlayEvent(SongId(song), at, 5_000, 200_000, EndedBy.SKIPPED, PlaySource.SHUFFLE_ALL, StartedBy.NAV, true)

    private fun scoreOf(
        song: Long,
        events: List<PlayEvent>,
    ) = scorer.score(events, now).single { it.songId == SongId(song) }.score

    @Test
    fun `one completed shuffle play now is 1`() {
        assertEquals(1.0, scoreOf(1, listOf(completed(1, now))))
    }

    @Test
    fun `the same play 30 days ago is 0_5`() {
        assertEquals(0.5, scoreOf(1, listOf(completed(1, now - 30 * day))))
    }

    @Test
    fun `an isolated early skip is -2`() {
        assertEquals(-2.0, scoreOf(1, listOf(quickSkip(1, now))))
    }

    @Test
    fun `a skip inside a browsing burst is -0_2`() {
        val burst = listOf(quickSkip(1, now), quickSkip(2, now + 5_000), quickSkip(3, now + 10_000))

        assertEquals(-0.2, scoreOf(1, burst), 1e-9)
        assertEquals(-0.2, scoreOf(3, burst), 1e-9)
    }

    @Test
    fun `completed clears that song's earlier skips but not another song's`() {
        val events = listOf(quickSkip(1, now - 30 * day), quickSkip(2, now - 30 * day), completed(1, now))

        assertEquals(1.0, scoreOf(1, events))
        assertEquals(-1.0, scoreOf(2, events))
    }

    @Test
    fun `a skip after a completion still counts`() {
        assertEquals(-1.5, scoreOf(1, listOf(completed(1, now - 30 * day), quickSkip(1, now))))
    }

    @Test
    fun `rows with equal started_at keep the order they are given in`() {
        assertEquals(-1.0, scoreOf(1, listOf(completed(1, now), quickSkip(1, now))))
        assertEquals(1.0, scoreOf(1, listOf(quickSkip(1, now), completed(1, now))))
    }

    @Test
    fun `the same input scores the same every time`() {
        val events = listOf(completed(1, now - day), quickSkip(2, now - day), completed(2, now))

        assertEquals(scorer.score(events, now), scorer.score(events, now))
    }

    @Test
    fun `empty log gives an empty list`() {
        assertTrue(scorer.score(emptyList(), now).isEmpty())
    }

    @Test
    fun `a row from the future never counts for more than its weight`() {
        assertEquals(1.0, scoreOf(1, listOf(completed(1, now + 10 * day))))
    }

    @Test
    fun `ties rank by latest play then song id`() {
        val pickedThenSkipped30DaysAgo =
            PlayEvent(
                SongId(9),
                now - 30 * day,
                20_000,
                200_000,
                EndedBy.SKIPPED,
                PlaySource.SONGS,
                StartedBy.PICKED,
                false,
            )

        val ranked = scorer.score(listOf(pickedThenSkipped30DaysAgo, completed(5, now), completed(3, now)), now)

        assertEquals(listOf(3L, 5L, 9L), ranked.map { it.songId.value })
        assertEquals(listOf(1.0, 1.0, 1.0), ranked.map { it.score })
    }
}
