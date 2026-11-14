package dev.tomasflopes.musicplayer.core.taste.suggest

import dev.tomasflopes.musicplayer.core.model.ArtistId
import dev.tomasflopes.musicplayer.core.model.Song
import dev.tomasflopes.musicplayer.core.model.SongId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SuggestionRankerTest {
    private val ranker = SuggestionRanker(setOf(ArtistLinkFilter(), AlbumLinkFilter(), TasteScoreFilter()))
    private val members = listOf(suggestSong(1, artist = 10, album = 5))

    private fun ids(songs: List<Song>) = songs.map { it.id.value }

    @Test
    fun `a song with no link to the playlist is never suggested, however much it is loved`() {
        val context =
            contextOf(members, songScores = mapOf(SongId(2) to 1e6), artistScores = mapOf(ArtistId(99) to 1e6))

        assertEquals(emptyList<Song>(), ranker.rank(listOf(suggestSong(2, artist = 99)), context, 10))
    }

    @Test
    fun `an empty playlist suggests nothing`() {
        assertEquals(emptyList<Song>(), ranker.rank(listOf(suggestSong(2)), contextOf(emptyList()), 10))
    }

    @Test
    fun `a same-artist song ranks above a same-album song by another artist`() {
        val sameArtist = suggestSong(3, artist = 10, album = 8)
        val sameAlbum = suggestSong(2, artist = 11, album = 5)

        val ranked = ranker.rank(listOf(sameAlbum, sameArtist), contextOf(members), 10)

        assertEquals(listOf(3L, 2L), ids(ranked))
    }

    @Test
    fun `taste breaks the order between equally related songs`() {
        val loved = suggestSong(3, artist = 10)
        val plain = suggestSong(2, artist = 10)
        val context = contextOf(members, songScores = mapOf(SongId(3) to 6.0))

        assertEquals(listOf(3L, 2L), ids(ranker.rank(listOf(plain, loved), context, 10)))
    }

    @Test
    fun `equal points go to the lower song id`() {
        val candidates = listOf(suggestSong(7, artist = 10), suggestSong(4, artist = 10))

        val ranked = ranker.rank(candidates, contextOf(members), 10)

        assertEquals(listOf(4L, 7L), ids(ranked))
    }

    @Test
    fun `only the best limit songs are kept`() {
        val candidates = (2L..9L).map { suggestSong(it, artist = 10) }

        assertEquals(3, ranker.rank(candidates, contextOf(members), 3).size)
    }

    @Test
    fun `a related song that the owner keeps skipping can drop out`() {
        val skipped = suggestSong(2, artist = 11, album = 5)
        val context =
            contextOf(
                members,
                songScores = mapOf(SongId(2) to -60.0),
                artistScores = mapOf(ArtistId(11) to -60.0),
            )

        assertEquals(emptyList<Song>(), ranker.rank(listOf(skipped), context, 10))
    }

    @Test
    fun `a dismissed neighbour ranks below a song with no dismissed neighbour`() {
        val dismissed = listOf(suggestSong(50, artist = 20))
        val context = contextOf(listOf(suggestSong(1, artist = 10), suggestSong(60, artist = 20)), dismissed)
        val nearDismissed = suggestSong(2, artist = 20)
        val clean = suggestSong(3, artist = 10)

        val withDismissed = SuggestionRanker(setOf(ArtistLinkFilter(), DismissedNeighbourFilter()))

        val ranked = withDismissed.rank(listOf(nearDismissed, clean), context, 10)

        assertEquals(listOf(3L, 2L), ids(ranked))
    }

    @Test
    fun `without filters nothing is suggested`() {
        val none = SuggestionRanker(emptySet())

        assertTrue(none.rank(listOf(suggestSong(2, artist = 10)), contextOf(members), 10).isEmpty())
    }
}
