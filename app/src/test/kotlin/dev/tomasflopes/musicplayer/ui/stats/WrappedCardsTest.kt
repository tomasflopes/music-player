package dev.tomasflopes.musicplayer.ui.stats

import dev.tomasflopes.musicplayer.core.model.ArtistId
import dev.tomasflopes.musicplayer.core.taste.stats.ListeningClock
import dev.tomasflopes.musicplayer.core.taste.stats.NewDiscoveries
import dev.tomasflopes.musicplayer.core.taste.stats.PeriodStats
import dev.tomasflopes.musicplayer.core.taste.stats.StatsPeriod
import dev.tomasflopes.musicplayer.fakes.testAlbum
import dev.tomasflopes.musicplayer.fakes.testSong
import dev.tomasflopes.musicplayer.ui.stats.cards.ComebackArtistCard
import dev.tomasflopes.musicplayer.ui.stats.cards.ListeningClockCard
import dev.tomasflopes.musicplayer.ui.stats.cards.MinutesListenedCard
import dev.tomasflopes.musicplayer.ui.stats.cards.MostSkippedCard
import dev.tomasflopes.musicplayer.ui.stats.cards.NewDiscoveriesCard
import dev.tomasflopes.musicplayer.ui.stats.cards.TopAlbumsCard
import dev.tomasflopes.musicplayer.ui.stats.cards.TopArtistsCard
import dev.tomasflopes.musicplayer.ui.stats.cards.TopSongsCard
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.YearMonth

class WrappedCardsTest {
    private val cards: List<WrappedCard> =
        listOf(
            TopSongsCard(),
            TopArtistsCard(),
            TopAlbumsCard(),
            MinutesListenedCard(),
            NewDiscoveriesCard(),
            MostSkippedCard(),
            ListeningClockCard(),
            ComebackArtistCard(),
        )
    private val period = StatsPeriod.Month(YearMonth.of(2026, 11))
    private val empty =
        WrappedData(PeriodStats.empty(period), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), null)

    private fun full(): WrappedData {
        val song = testSong(1)
        val clock = ListeningClock.EMPTY.copy(playsByHour = List(ListeningClock.HOURS) { if (it == 9) 5 else 0 })
        val stats =
            PeriodStats.empty(period).copy(
                totalPlays = 5,
                listenedMs = 600_000,
                newDiscoveries = NewDiscoveries(3, emptyList()),
                clock = clock,
            )
        return WrappedData(
            stats = stats,
            topSongs = listOf(RankedSong(song, 5)),
            topArtists = listOf(RankedArtist(ArtistId(1), "Artist", 5)),
            topAlbums = listOf(RankedAlbum(testAlbum(1, "Album", "Artist"), song, 5)),
            mostSkipped = listOf(RankedSong(song, 3)),
            discoveries = listOf(song),
            comeback = ComebackData(RankedArtist(ArtistId(1), "Artist", 9), previousPlays = 2),
        )
    }

    @Test
    fun `the eight cards of the design are registered, in the design's order`() {
        assertEquals(
            listOf(
                "top_songs",
                "top_artists",
                "top_albums",
                "minutes",
                "discoveries",
                "most_skipped",
                "clock",
                "comeback",
            ),
            cards.sortedBy { it.order }.map { it.id },
        )
    }

    @Test
    fun `ids and orders are unique`() {
        assertEquals(cards.size, cards.map { it.id }.toSet().size)
        assertEquals(cards.size, cards.map { it.order }.toSet().size)
    }

    @Test
    fun `no card has content in an empty period`() = cards.forEach { assertFalse(it.hasContent(empty), it.id) }

    @Test
    fun `every card has content when its data is there`() = cards.forEach { assertTrue(it.hasContent(full()), it.id) }

    @Test
    fun `less than a minute listened is not worth a card`() {
        val minutes = cards.first { it.id == "minutes" }
        val under = full().let { it.copy(stats = it.stats.copy(listenedMs = 59_999)) }

        assertFalse(minutes.hasContent(under))
    }

    @Test
    fun `an album without a cover song still counts`() {
        val data = full().let { it.copy(topAlbums = listOf(RankedAlbum(testAlbum(1, "Album", "Artist"), null, 5))) }

        assertTrue(cards.first { it.id == "top_albums" }.hasContent(data))
    }
}
