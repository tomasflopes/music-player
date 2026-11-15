package dev.tomasflopes.musicplayer.core.taste.score

import dev.tomasflopes.musicplayer.core.model.SongId
import dev.tomasflopes.musicplayer.core.model.log.EndedBy
import dev.tomasflopes.musicplayer.core.model.log.PlayEvent
import dev.tomasflopes.musicplayer.core.model.log.PlaySource
import dev.tomasflopes.musicplayer.core.model.log.PlaylistAddEvent
import dev.tomasflopes.musicplayer.core.model.log.StartedBy
import dev.tomasflopes.musicplayer.core.taste.config.DecayRule
import dev.tomasflopes.musicplayer.core.taste.config.TasteConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.pow

class PastScorerTest {
    private val day = 86_400_000L
    private val now = 1_000 * day
    private val asOf = now - 60 * day
    private val past = PastScorer()

    /** A picked play of [song] that went to the end, outside shuffle: worth +3 on its own. */
    private fun loved(
        song: Long,
        at: Long,
    ) = PlayEvent(SongId(song), at, 200_000, 200_000, EndedBy.COMPLETED, PlaySource.ALBUM, StartedBy.PICKED, false)

    private fun scoreOf(
        song: Long,
        signals: TasteSignals,
    ) = past.songsAsOf(signals, asOf).single { it.songId == SongId(song) }.score

    @Test
    fun `a play at the moment is worth its full weight, one 30 days earlier half`() {
        assertEquals(3.0, scoreOf(1, TasteSignals(plays = listOf(loved(1, asOf - 1)))), 1e-6)
        assertEquals(1.5, scoreOf(1, TasteSignals(plays = listOf(loved(1, asOf - 30 * day)))))
    }

    @Test
    fun `rows at or after the moment are not read`() {
        val plays = listOf(loved(1, asOf - 30 * day), loved(1, asOf), loved(1, asOf + 5 * day), loved(2, now))

        val songs = past.songsAsOf(TasteSignals(plays = plays), asOf)

        assertEquals(listOf(SongId(1)), songs.map { it.songId })
        assertEquals(1.5, songs.single().score)
    }

    @Test
    fun `it is the scorer's own answer for the earlier rows at the moment`() {
        val plays = listOf(loved(1, asOf - 10 * day), loved(2, asOf - 50 * day), loved(1, asOf + 20 * day))

        val expected = TasteScorer().score(plays.filter { it.startedAtEpochMs < asOf }, asOf)

        assertEquals(expected, past.songsAsOf(TasteSignals(plays = plays), asOf))
    }

    @Test
    fun `today's scores are computed as before`() {
        val plays = listOf(loved(1, asOf - 10 * day), loved(1, now))

        val today = TasteScorer().scoreAll(TasteSignals(plays = plays), emptyMap(), now).songs.single()

        assertEquals(3.0 + 3.0 * 0.5.pow(70.0 / 30.0), today.score, 1e-9)
    }

    @Test
    fun `rows older than the decay horizon from the moment are dropped`() {
        val horizon = TasteConfig().decay.horizonMs
        val plays = listOf(loved(1, asOf - horizon - 1), loved(2, asOf - horizon))

        val songs = past.songsAsOf(TasteSignals(plays = plays), asOf)

        assertEquals(listOf(SongId(2)), songs.map { it.songId })
        assertEquals(asOf - horizon, past.oldestRowFor(asOf))
    }

    @Test
    fun `a shorter horizon from the config is used`() {
        val short = PastScorer(TasteConfig(decay = DecayRule(horizonDays = 20)))
        val plays = listOf(loved(1, asOf - 25 * day), loved(2, asOf - 15 * day))

        assertEquals(listOf(SongId(2)), short.songsAsOf(TasteSignals(plays = plays), asOf).map { it.songId })
    }

    @Test
    fun `a playlist add before the moment counts and one after does not`() {
        val adds = listOf(PlaylistAddEvent(SongId(1), 1, asOf - 30 * day), PlaylistAddEvent(SongId(2), 1, asOf + day))

        val songs = past.songsAsOf(TasteSignals(playlistAdds = adds), asOf)

        assertEquals(listOf(SongId(1)), songs.map { it.songId })
        assertEquals(1.5, songs.single().score)
    }

    @Test
    fun `an empty log gives no scores`() {
        assertTrue(past.songsAsOf(TasteSignals(), asOf).isEmpty())
    }
}
