package dev.tomasflopes.musicplayer.core.taste.suggest

import dev.tomasflopes.musicplayer.core.model.Song
import dev.tomasflopes.musicplayer.core.model.SongId
import dev.tomasflopes.musicplayer.core.model.playlists.InMemoryDismissals
import dev.tomasflopes.musicplayer.core.model.playlists.InMemoryPlaylists
import dev.tomasflopes.musicplayer.core.model.playlists.PlaylistId
import dev.tomasflopes.musicplayer.core.taste.config.SuggestionConfig
import dev.tomasflopes.musicplayer.core.taste.lists.FakeScoreBoard
import dev.tomasflopes.musicplayer.core.taste.lists.FakeSongReader
import dev.tomasflopes.musicplayer.core.taste.score.SongScore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.coroutines.EmptyCoroutineContext

@OptIn(ExperimentalCoroutinesApi::class)
class PlaylistSuggestionsTest {
    private val ranker =
        SuggestionRanker(
            setOf(ArtistLinkFilter(), AlbumLinkFilter(), TasteScoreFilter(), DismissedNeighbourFilter()),
        )
    private val dismissals = InMemoryDismissals()

    private fun suggestions(
        library: List<Song>,
        playlists: InMemoryPlaylists,
        ranked: List<SongScore> = emptyList(),
        artists: FakeSongArtists = FakeSongArtists(library),
        config: SuggestionConfig = SuggestionConfig(),
    ) = PlaylistSuggestions(
        playlists = playlists,
        dismissals = dismissals,
        library = FakeSongReader(library),
        artists = artists,
        board = FakeScoreBoard(ranked),
        ranker = ranker,
        config = config,
        computeOn = EmptyCoroutineContext,
    )

    private suspend fun playlistWith(
        playlists: InMemoryPlaylists,
        vararg songs: Long,
    ): PlaylistId {
        val id = playlists.create("Gym", 1)
        playlists.addSongs(id, songs.map(::SongId), 1)
        return id
    }

    private suspend fun PlaylistSuggestions.page(id: PlaylistId) =
        observe(id, SuggestionPlace.PLAYLIST_PAGE).first().map { it.id.value }

    @Test
    fun `playlist songs and dismissed songs are never suggested`() =
        runTest {
            val library = (1L..6L).map { suggestSong(it, artist = 10) }
            val playlists = InMemoryPlaylists(library)
            val id = playlistWith(playlists, 1, 2)
            dismissals.dismiss(id, SongId(3), 1)

            assertEquals(listOf(4L, 5L, 6L), suggestions(library, playlists).page(id))
        }

    @Test
    fun `an empty playlist suggests nothing`() =
        runTest {
            val library = (1L..4L).map { suggestSong(it, artist = 10) }
            val playlists = InMemoryPlaylists(library)

            assertEquals(emptyList<Long>(), suggestions(library, playlists).page(playlistWith(playlists)))
        }

    @Test
    fun `songs with no link to the playlist are left out`() =
        runTest {
            val library = listOf(suggestSong(1, artist = 10), suggestSong(2, artist = 10), suggestSong(3, artist = 30))
            val playlists = InMemoryPlaylists(library)

            assertEquals(listOf(2L), suggestions(library, playlists).page(playlistWith(playlists, 1)))
        }

    @Test
    fun `a library that is all in the playlist suggests nothing`() =
        runTest {
            val library = (1L..3L).map { suggestSong(it, artist = 10) }
            val playlists = InMemoryPlaylists(library)

            assertEquals(emptyList<Long>(), suggestions(library, playlists).page(playlistWith(playlists, 1, 2, 3)))
        }

    @Test
    fun `about ten are shown, never more`() =
        runTest {
            val library = (1L..16L).map { suggestSong(it, artist = 10) }
            val playlists = InMemoryPlaylists(library)

            assertEquals(10, suggestions(library, playlists).page(playlistWith(playlists, 1)).size)
        }

    @Test
    fun `a song the owner loves comes first among equally related songs`() =
        runTest {
            val library = (1L..4L).map { suggestSong(it, artist = 10) }
            val playlists = InMemoryPlaylists(library)
            val ranked = listOf(SongScore(SongId(4), 6.0, lastPlayedAtEpochMs = 0))

            val shown = suggestions(library, playlists, ranked).page(playlistWith(playlists, 1))

            assertEquals(listOf(4L, 2L, 3L), shown)
        }

    @Test
    fun `a song that features a playlist artist is suggested`() =
        runTest {
            val library = listOf(suggestSong(1, artist = 10), suggestSong(2, artist = 20), suggestSong(3, artist = 30))
            val playlists = InMemoryPlaylists(library)
            val artists = FakeSongArtists(library, featured = mapOf(2L to listOf(10L)))

            val shown = suggestions(library, playlists, artists = artists).page(playlistWith(playlists, 1))

            assertEquals(listOf(2L), shown)
        }

    @Test
    fun `dismissing a song pushes its artist down in that playlist`() =
        runTest {
            val library =
                listOf(
                    suggestSong(1, artist = 10),
                    suggestSong(2, artist = 20),
                    suggestSong(3, artist = 20),
                    suggestSong(4, artist = 10),
                    suggestSong(5, artist = 20),
                )
            val playlists = InMemoryPlaylists(library)
            val id = playlistWith(playlists, 1, 2)
            val before = suggestions(library, playlists).page(id)

            dismissals.dismiss(id, SongId(5), 1)

            assertEquals(listOf(3L, 4L, 5L), before)
            assertEquals(listOf(4L, 3L), suggestions(library, playlists).page(id))
        }

    @Test
    fun `the picker waits for two songs, the page for one`() =
        runTest {
            val library = (1L..5L).map { suggestSong(it, artist = 10) }
            val playlists = InMemoryPlaylists(library)
            val id = playlistWith(playlists, 1)
            val subject = suggestions(library, playlists)

            assertEquals(emptyList<Song>(), subject.observe(id, SuggestionPlace.NEW_PLAYLIST_PICKER).first())
            assertTrue(subject.page(id).isNotEmpty())

            playlists.addSongs(id, listOf(SongId(2)), 2)

            assertTrue(subject.observe(id, SuggestionPlace.NEW_PLAYLIST_PICKER).first().isNotEmpty())
        }

    @Test
    fun `adding a song or dismissing one refreshes the list that is being watched`() =
        runTest {
            val library = (1L..5L).map { suggestSong(it, artist = 10) }
            val playlists = InMemoryPlaylists(library)
            val id = playlistWith(playlists, 1)
            val seen = mutableListOf<List<Long>>()
            val subject = suggestions(library, playlists)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                subject.observe(id, SuggestionPlace.PLAYLIST_PAGE).collect { shown ->
                    seen += shown.map { it.id.value }
                }
            }

            playlists.addSongs(id, listOf(SongId(2)), 2)
            dismissals.dismiss(id, SongId(3), 3)

            assertEquals(listOf(listOf(2L, 3L, 4L, 5L), listOf(3L, 4L, 5L), listOf(4L, 5L)), seen)
        }

    @Test
    fun `each song's artists are looked up once while the list is watched`() =
        runTest {
            val library = (1L..5L).map { suggestSong(it, artist = 10) }
            val playlists = InMemoryPlaylists(library)
            val id = playlistWith(playlists, 1)
            val artists = FakeSongArtists(library)
            val subject = suggestions(library, playlists, artists = artists)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                subject.observe(id, SuggestionPlace.PLAYLIST_PAGE).collect {}
            }

            playlists.addSongs(id, listOf(SongId(2)), 2)
            playlists.addSongs(id, listOf(SongId(3)), 3)

            assertEquals(library.map { it.id }.toSet(), artists.asked.flatten().toSet())
            assertEquals(library.size, artists.asked.flatten().size)
        }
}
