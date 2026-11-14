package dev.tomasflopes.musicplayer.core.taste.score

import dev.tomasflopes.musicplayer.core.model.ArtistId
import dev.tomasflopes.musicplayer.core.model.SongArtists
import dev.tomasflopes.musicplayer.core.model.SongId
import dev.tomasflopes.musicplayer.core.model.log.EndedBy
import dev.tomasflopes.musicplayer.core.model.log.PlayEvent
import dev.tomasflopes.musicplayer.core.model.log.PlaySource
import dev.tomasflopes.musicplayer.core.model.log.PlaylistAddEvent
import dev.tomasflopes.musicplayer.core.model.log.SearchEvent
import dev.tomasflopes.musicplayer.core.model.log.SearchItemType
import dev.tomasflopes.musicplayer.core.model.log.StartedBy
import dev.tomasflopes.musicplayer.core.taste.config.SearchRule
import dev.tomasflopes.musicplayer.core.taste.config.TasteConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Slice A of phase 2 (ADR 0049): artist scores, added to a playlist, searches. */
class ArtistScoringTest {
    private val day = 86_400_000L
    private val now = 1_000 * day
    private val scorer = TasteScorer()

    private val main = ArtistId(1)
    private val guest = ArtistId(2)
    private val artists =
        mapOf(
            SongId(10) to SongArtists(main, listOf(guest)),
            SongId(11) to SongArtists(main),
            SongId(20) to SongArtists(guest),
        )

    private fun completed(
        song: Long,
        at: Long = now,
    ) = PlayEvent(SongId(song), at, 200_000, 200_000, EndedBy.COMPLETED, PlaySource.SHUFFLE_ALL, StartedBy.AUTO, true)

    private fun shufflePlay(
        song: Long,
        at: Long,
        by: StartedBy = StartedBy.AUTO,
    ) = PlayEvent(
        SongId(song),
        at,
        200_000,
        200_000,
        EndedBy.COMPLETED,
        PlaySource.ARTIST_SHUFFLE,
        StartedBy.PICKED.takeIf { by == StartedBy.PICKED } ?: by,
        true,
    )

    private fun scores(signals: TasteSignals) = scorer.scoreAll(signals, artists, now)

    private fun TasteScores.artist(id: ArtistId) = artists.singleOrNull { it.artistId == id }?.score

    private fun TasteScores.song(id: Long) = songs.singleOrNull { it.songId == SongId(id) }?.score

    @Test
    fun `the main artist gets the whole song score and a featured artist 0_3 of it`() {
        val result = scores(TasteSignals(plays = listOf(completed(10))))

        assertEquals(1.0, result.artist(main))
        assertEquals(0.3, result.artist(guest)!!, 1e-12)
    }

    @Test
    fun `an artist adds up its songs`() {
        val result = scores(TasteSignals(plays = listOf(completed(10), completed(11))))

        assertEquals(2.0, result.artist(main))
    }

    @Test
    fun `a negative song score lowers the artist, a featured artist only by 0_3`() {
        val skip =
            PlayEvent(SongId(10), now, 5_000, 200_000, EndedBy.SKIPPED, PlaySource.SHUFFLE_ALL, StartedBy.NAV, true)

        val result = scores(TasteSignals(plays = listOf(skip)))

        assertEquals(-2.0, result.artist(main))
        assertEquals(-0.6, result.artist(guest)!!, 1e-12)
    }

    @Test
    fun `a song the library does not know adds nothing to any artist`() {
        val result = scores(TasteSignals(plays = listOf(completed(99))))

        assertEquals(1.0, result.song(99))
        assertEquals(emptyList<ArtistScore>(), result.artists)
    }

    @Test
    fun `adding a song to a playlist is worth 3 on the song and on its artist`() {
        val result = scores(TasteSignals(playlistAdds = listOf(PlaylistAddEvent(SongId(11), 5, now))))

        assertEquals(3.0, result.song(11))
        assertEquals(3.0, result.artist(main))
    }

    @Test
    fun `a playlist add decays like any signal`() {
        val result = scores(TasteSignals(playlistAdds = listOf(PlaylistAddEvent(SongId(11), 5, now - 30 * day))))

        assertEquals(1.5, result.song(11)!!, 1e-12)
    }

    @Test
    fun `a completed play does not forgive anything about a playlist add`() {
        val skip =
            PlayEvent(
                SongId(11),
                now - day,
                5_000,
                200_000,
                EndedBy.SKIPPED,
                PlaySource.SHUFFLE_ALL,
                StartedBy.NAV,
                true,
            )
        val signals =
            TasteSignals(
                plays = listOf(skip, completed(11)),
                playlistAdds = listOf(PlaylistAddEvent(SongId(11), 5, now)),
            )

        assertEquals(4.0, scores(signals).song(11)!!, 0.01)
    }

    @Test
    fun `starting an artist shuffle adds 2 on the artist and nothing on a song`() {
        val start = shufflePlay(11, now, StartedBy.PICKED).copy(endedBy = EndedBy.STOPPED, listenedMs = 1_000)

        val result = scores(TasteSignals(plays = listOf(start)))

        assertEquals(2.0 + result.song(11)!!, result.artist(main)!!, 1e-12)
    }

    @Test
    fun `the rows after the first of a shuffle are not new starts`() {
        val rows =
            listOf(shufflePlay(11, now - 60_000, StartedBy.PICKED), shufflePlay(10, now - 30_000), shufflePlay(11, now))

        val result = scores(TasteSignals(plays = rows))

        // 2 for the start; song 11 earns 4 + 1 (picked, then completed under shuffle) and song 10 earns 1.
        assertEquals(8.0, result.artist(main)!!, 0.01)
    }

    @Test
    fun `a pause longer than the gap, or another source in between, starts a second shuffle`() {
        val later = now - 10 * 60_000
        val separate =
            listOf(shufflePlay(11, now - 3 * 3_600_000, StartedBy.PICKED), shufflePlay(11, later, StartedBy.PICKED))
        val interrupted =
            listOf(
                shufflePlay(11, now - 60_000, StartedBy.PICKED),
                completed(20, now - 30_000),
                shufflePlay(11, now, StartedBy.PICKED),
            )

        assertEquals(2, ArtistShuffleStarts().find(separate, artists).size)
        assertEquals(2, ArtistShuffleStarts().find(interrupted, artists).size)
    }

    @Test
    fun `a jump inside a shuffle is not a start, and a shuffle that begins unpicked is not one either`() {
        val jump =
            listOf(
                shufflePlay(11, now - 120_000, StartedBy.PICKED),
                shufflePlay(10, now - 60_000),
                shufflePlay(11, now, StartedBy.PICKED),
            )
        val resumed = listOf(shufflePlay(11, now))

        assertEquals(1, ArtistShuffleStarts().find(jump, artists).size)
        assertEquals(emptyList<ArtistShuffleStart>(), ArtistShuffleStarts().find(resumed, artists))
    }

    @Test
    fun `the artist of a shuffle is the one most of its songs belong to, not the one of the first song`() {
        val rows =
            listOf(
                shufflePlay(20, now - 120_000, StartedBy.PICKED),
                shufflePlay(11, now - 60_000),
                shufflePlay(10, now),
            )

        assertEquals(main, ArtistShuffleStarts().find(rows, artists).single().artistId)
    }

    @Test
    fun `choosing an artist in search is worth 1 on the artist and nothing on a song`() {
        val search = SearchEvent("rui", SearchItemType.ARTIST, main.value, now)

        val result = scores(TasteSignals(searches = listOf(search)))

        assertEquals(1.0, result.artist(main))
        assertEquals(emptyList<SongScore>(), result.songs)
    }

    @Test
    fun `choosing a song or an album in search adds nothing of its own`() {
        val searches =
            listOf(SearchEvent("a", SearchItemType.SONG, 11, now), SearchEvent("b", SearchItemType.ALBUM, 3, now))

        val result = scores(TasteSignals(searches = searches))

        assertEquals(emptyList<SongScore>(), result.songs)
        assertEquals(emptyList<ArtistScore>(), result.artists)
    }

    @Test
    fun `a retuned search rule counts a chosen song`() {
        val tuned = TasteScorer(TasteConfig(search = SearchRule(song = 0.5, artist = 0.0)))
        val searches =
            listOf(SearchEvent("a", SearchItemType.SONG, 11, now), SearchEvent("b", SearchItemType.ARTIST, 1, now))

        val result = tuned.scoreAll(TasteSignals(searches = searches), artists, now)

        assertEquals(0.5, result.song(11))
        // The artist search added nothing: the 0.5 is the song's own score rolled up.
        assertEquals(0.5, result.artist(main))
    }

    @Test
    fun `the same logs give the same scores, so a recompute from the log is the whole rebuild`() {
        val signals =
            TasteSignals(
                plays = listOf(completed(10, now - day), shufflePlay(11, now - 60_000, StartedBy.PICKED)),
                playlistAdds = listOf(PlaylistAddEvent(SongId(20), 1, now - 2 * day)),
                searches = listOf(SearchEvent("x", SearchItemType.ARTIST, 2, now - day)),
            )

        assertEquals(scores(signals), scores(signals))
    }

    @Test
    fun `scoring songs alone is unchanged by the artist work`() {
        assertEquals(
            scorer.score(listOf(completed(10)), now),
            scores(TasteSignals(plays = listOf(completed(10)))).songs,
        )
    }

    @Test
    fun `artists are ranked by score, then by latest signal, then by id`() {
        val result = scores(TasteSignals(plays = listOf(completed(11), completed(20, now - day))))

        assertEquals(listOf(main, guest), result.artists.map { it.artistId })
    }
}
