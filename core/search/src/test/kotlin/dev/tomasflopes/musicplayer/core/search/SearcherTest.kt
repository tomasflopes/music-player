package dev.tomasflopes.musicplayer.core.search

import dev.tomasflopes.musicplayer.core.model.Album
import dev.tomasflopes.musicplayer.core.model.AlbumId
import dev.tomasflopes.musicplayer.core.model.Artist
import dev.tomasflopes.musicplayer.core.model.ArtistId
import dev.tomasflopes.musicplayer.core.model.Song
import dev.tomasflopes.musicplayer.core.model.SongId
import dev.tomasflopes.musicplayer.core.model.log.SearchItemType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

private fun artist(
    id: Long,
    name: String,
) = Artist(ArtistId(id), name, songCount = 1, albumCount = 1)

private fun album(
    id: Long,
    title: String,
    artist: Artist,
) = Album(AlbumId(id), title, artist.name, songCount = 1, coverSongId = null)

private fun song(
    id: Long,
    title: String,
    artist: Artist,
    album: Album? = null,
    folder: String = "Music/Test",
) = Song(
    id = SongId(id),
    title = title,
    artistId = artist.id,
    artistName = artist.name,
    albumId = album?.id,
    albumTitle = album?.title,
    albumArtist = album?.albumArtist,
    trackNumber = null,
    discNumber = null,
    durationMs = 0,
    volumeName = "external_primary",
    path = "$folder/$title.mp3",
    fileName = "$title.mp3",
    folderPath = folder,
    dateAddedEpochSec = 0,
    contentUri = "content://media/external_primary/audio/media/$id",
    hasCover = false,
)

class SearcherTest {
    private val ed = artist(1, "Ed")
    private val eddie = artist(2, "Eddie Kane")
    private val kendrick = artist(3, "Kendrick Lamar")
    private val asap = artist(4, "A\$AP Rocky")
    private val jayZ = artist(5, "JAY-Z")
    private val nwa = artist(6, "N.W.A")
    private val beyonce = artist(7, "Beyoncé")
    private val sample = artist(8, "Sample B")

    private val education = album(10, "Education", eddie)
    private val damn = album(11, "DAMN.", kendrick)
    private val morale = album(12, "Mr. Morale", kendrick)

    private val morning = song(100, "Morning", ed)
    private val night = song(101, "Night feat. Ed", kendrick, damn)
    private val faded = song(102, "Faded", sample)
    private val ended = song(103, "Ended", sample)
    private val maad = song(104, "m.A.A.d city", kendrick, damn)
    private val remix = song(105, "Song (Remix)", kendrick, morale)
    private val bigEd = song(106, "Big Ed", eddie, education)
    private val humble = song(107, "HUMBLE.", kendrick, damn)

    private val songs =
        listOf(morning, night, faded, ended, maad, remix, bigEd, humble)
    private val artists = listOf(ed, eddie, kendrick, asap, jayZ, nwa, beyonce, sample)
    private val albums = listOf(education, damn, morale)

    private fun searcher(
        extraMatchers: Set<SearchMatcher> = emptySet(),
        config: SearchConfig = SearchConfig(),
    ): Searcher {
        val index = SearchIndex(FieldMatcher.defaults() + extraMatchers)
        index.rebuild(artists, albums, songs, mapOf(night.id to listOf("Ed")))
        return Searcher(index, config)
    }

    @Test
    fun `Ed puts the artist Ed first`() {
        val result = searcher().search("Ed")
        assertEquals(SearchItemType.ARTIST, result.top?.type)
        assertEquals("Ed", (result.top as ArtistHit).artist.name)
    }

    @Test
    fun `exact before starts-with`() =
        assertEquals(listOf("Ed", "Eddie Kane"), searcher().search("ed").artists.map { it.artist.name })

    @Test
    fun `mid-word matches are not in the main results`() {
        val songs = searcher().search("ed").songs.map { it.song.title }
        assertTrue("Faded" !in songs)
        assertTrue("Ended" !in songs)
    }

    @Test
    fun `mid-word matches are last under See all`() {
        val all = searcher().searchAll("ed", SearchItemType.SONG).map { (it as SongHit).song.title }
        assertTrue(all.containsAll(listOf("Faded", "Ended")))
        assertTrue(all.indexOf("Big Ed") < all.indexOf("Faded"))
    }

    @Test
    fun `featured and own songs are found by artist name`() {
        val songs = searcher().searchAll("ed", SearchItemType.SONG).map { (it as SongHit).song.title }
        assertTrue(songs.containsAll(listOf("Morning", "Night feat. Ed")))
    }

    @Test
    fun `case, spaces and accents do not matter`() =
        assertEquals(searcher().search("ed"), searcher().search("  ÉD ").copy(query = "ed"))

    @Test
    fun `multi-word prefix finds the artist first`() {
        val result = searcher().search("kendrick l")
        assertEquals(SearchItemType.ARTIST, result.top?.type)
        assertEquals("Kendrick Lamar", (result.top as ArtistHit).artist.name)
    }

    @ParameterizedTest
    @ValueSource(strings = ["jayz", "jay z", "jay-z", "JAY Z", "Jay.Z"])
    fun `JAY-Z is found with or without its symbols`(query: String) {
        assertEquals("JAY-Z", (searcher().search(query).top as ArtistHit).artist.name)
    }

    @ParameterizedTest
    @CsvSource(
        "asap rocky,A\$AP Rocky",
        "a\$ap,A\$AP Rocky",
        "maad city,m.A.A.d city",
        "m.a.a.d,m.A.A.d city",
        "nwa,N.W.A",
        "n.w.a,N.W.A",
        "damn,DAMN.",
        "song remix,Song (Remix)",
        "(remix),Song (Remix)",
        "beyonce,Beyoncé",
        "mr morale,Mr. Morale",
    )
    fun `accents and symbols are ignored when matching, and the hit is shown as tagged`(
        query: String,
        display: String,
    ) {
        assertEquals(display, searcher().search(query).top?.display())
    }

    @Test
    fun `sections hold at most four and say when there is more`() {
        val manyArtists =
            artists + (20L..25L).map { id -> artist(id, "Ed Artist $id") }
        val index = SearchIndex()
        index.rebuild(manyArtists, albums, songs, emptyMap())
        val result = Searcher(index).search("ed")

        assertEquals(4, result.artists.size)
        assertTrue(result.moreArtists)
    }

    @Test
    fun `blank query is empty`() {
        val result = searcher().search("   ")
        assertNull(result.top)
        assertTrue(result.artists.isEmpty())
        assertTrue(result.albums.isEmpty())
        assertTrue(result.songs.isEmpty())
    }

    @Test
    fun `a matcher added to the set is used without touching SearchIndex`() {
        val custom =
            object : SearchMatcher {
                override val field = SearchField.FOLDER

                override fun match(
                    queryVariants: Set<String>,
                    value: String,
                ): MatchTier? = if ("special" in queryVariants) MatchTier.EXACT else null
            }
        val special = song(200, "Ordinary", sample, folder = "Music/Special Place")
        val index = SearchIndex((FieldMatcher.defaults().filterNot { it.field == SearchField.FOLDER } + custom).toSet())
        index.rebuild(artists, albums, songs + special, emptyMap())

        val hit = Searcher(index).searchAll("special", SearchItemType.SONG).single() as SongHit
        assertEquals(MatchTier.EXACT, hit.tier)
        assertEquals(SearchField.FOLDER, hit.field)
    }

    private fun SearchHit.display(): String =
        when (this) {
            is ArtistHit -> artist.name
            is AlbumHit -> album.title
            is SongHit -> song.title
        }
}
