package dev.tomasflopes.musicplayer.core.search

import dev.tomasflopes.musicplayer.core.model.Album
import dev.tomasflopes.musicplayer.core.model.AlbumId
import dev.tomasflopes.musicplayer.core.model.Artist
import dev.tomasflopes.musicplayer.core.model.ArtistId
import dev.tomasflopes.musicplayer.core.model.Song
import dev.tomasflopes.musicplayer.core.model.SongId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

private fun song(
    id: Long,
    title: String = "Song",
    artist: String = "Artist",
    album: String? = null,
    albumArtist: String? = null,
    featured: List<String> = emptyList(),
): IndexableSong =
    IndexableSong(
        id = SongId(id),
        artistId = ArtistId(id),
        title = title,
        artist = artist,
        albumId = if (album == null) null else AlbumId(id),
        album = album,
        albumArtist = albumArtist,
        folderName = null,
        featured = featured,
    )

private fun domainSong(
    id: Long,
    title: String = "Song",
    artist: String = "Artist",
    albumId: Long? = null,
    albumTitle: String? = null,
): Song =
    Song(
        id = SongId(id),
        title = title,
        artistId = ArtistId(id),
        artistName = artist,
        albumId = albumId?.let(::AlbumId),
        albumTitle = albumTitle,
        albumArtist = null,
        trackNumber = null,
        discNumber = null,
        durationMs = 0,
        volumeName = "",
        path = "",
        fileName = "",
        folderPath = "",
        dateAddedEpochSec = 0,
        contentUri = "",
        hasCover = false,
    )

class SearcherTest {
    private val artistsDomain =
        listOf(
            Artist(ArtistId(1), "Ed Sheeran", 1, 1),
            Artist(ArtistId(2), "Kendrick Lamar", 1, 1),
        )
    private val albumsDomain =
        listOf(
            Album(AlbumId(1), "Divide", "Ed Sheeran", 1, null),
            Album(AlbumId(2), "DAMN.", "Kendrick Lamar", 1, null),
        )

    private fun searcher(songs: List<IndexableSong>): Searcher {
        val index = SearchIndex()
        index.rebuild(songs)
        return Searcher(index, albumsDomain, artistsDomain)
    }

    @Test
    fun exact_title_match_ranks_first() {
        val songs =
            listOf(
                song(1, "HUMBLE.", "Kendrick Lamar", album = "DAMN."),
                song(2, "HUMBLE. Remix", "Kendrick Lamar"),
            )
        val ids = mapOf(1L to domainSong(1, "HUMBLE."), 2L to domainSong(2, "HUMBLE. Remix"))
        val result = searcher(songs).search("HUMBLE.", ids)

        assertNotNull(result.top)
        assertEquals("HUMBLE.", result.top!!.song.title)
        assertEquals(MatchTier.EXACT, result.top!!.tier)
    }

    @Test
    fun starts_with_match_ranks_below_exact() {
        val songs =
            listOf(
                song(1, "humblebee", "Kendrick Lamar"),
                song(2, "humble", "Kendrick Lamar"),
            )
        val ids = mapOf(1L to domainSong(1, "humblebee"), 2L to domainSong(2, "humble"))
        val result = searcher(songs).search("humble", ids)

        assertEquals("humble", result.top!!.song.title)
        assertEquals(MatchTier.EXACT, result.top!!.tier)
        assertEquals(listOf("humblebee"), result.songs.map { it.song.title })
        assertEquals(listOf(MatchTier.STARTS_WITH), result.songs.map { it.tier })
    }

    @Test
    fun matches_artist_and_album() {
        val songs =
            listOf(
                song(1, "HUMBLE.", "Kendrick Lamar", album = "DAMN.", albumArtist = "Kendrick Lamar"),
            )
        val ids = mapOf(1L to domainSong(1, "HUMBLE.", albumId = 1, albumTitle = "DAMN."))
        val result = searcher(songs).search("damn.", ids)

        assertNotNull(result.top)
        assertEquals(MatchTier.EXACT, result.top!!.tier)
        assertEquals(1, result.albums.size)
        assertEquals(emptyList<HitSong>(), result.songs)
    }

    @Test
    fun matches_featured_artist() {
        val songs =
            listOf(
                song(1, "Song feat. Ed Sheeran", "Artist", featured = listOf("Ed Sheeran")),
            )
        val result = searcher(songs).search("ed", mapOf(1L to domainSong(1, "Song feat. Ed Sheeran")))

        assertNotNull(result.top)
        assertEquals(MatchTier.STARTS_WITH, result.top!!.tier)
        assertEquals(emptyList<HitSong>(), result.songs)
    }

    @Test
    fun empty_query_returns_empty() {
        val result = searcher(emptyList()).search("", emptyMap())
        assertEquals(null, result.top)
        assertEquals(0, result.songs.size)
    }
}
