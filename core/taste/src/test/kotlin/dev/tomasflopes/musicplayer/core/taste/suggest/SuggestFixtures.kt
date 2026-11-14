package dev.tomasflopes.musicplayer.core.taste.suggest

import dev.tomasflopes.musicplayer.core.model.AlbumId
import dev.tomasflopes.musicplayer.core.model.ArtistId
import dev.tomasflopes.musicplayer.core.model.Song
import dev.tomasflopes.musicplayer.core.model.SongArtists
import dev.tomasflopes.musicplayer.core.model.SongId
import dev.tomasflopes.musicplayer.core.model.ports.SongArtistSource

/** A song by [artist] (the main artist's id), optionally on [album]. */
internal fun suggestSong(
    id: Long,
    artist: Long = id,
    album: Long? = null,
) = Song(
    id = SongId(id),
    title = "Song $id",
    artistId = ArtistId(artist),
    artistName = "Artist $artist",
    albumId = album?.let(::AlbumId),
    albumTitle = album?.let { "Album $it" },
    albumArtist = null,
    trackNumber = null,
    discNumber = null,
    durationMs = 1_000,
    volumeName = "external_primary",
    path = "Music/$id.mp3",
    fileName = "$id.mp3",
    folderPath = "Music",
    dateAddedEpochSec = 0,
    contentUri = "content://media/external/audio/media/$id",
    hasCover = false,
)

/** The artists of the songs in [library] (main artist from the song), plus [featured] artists per song id. */
internal class FakeSongArtists(
    private val library: List<Song> = emptyList(),
    private val featured: Map<Long, List<Long>> = emptyMap(),
) : SongArtistSource {
    val asked = mutableListOf<Collection<SongId>>()

    override suspend fun artistsOf(songs: Collection<SongId>): Map<SongId, SongArtists> {
        asked += songs.toList()
        return songs.associateWith { id ->
            SongArtists(
                main = library.first { it.id == id }.artistId,
                featured = featured[id.value].orEmpty().map(::ArtistId),
            )
        }
    }
}

/** A context where the playlist holds [members] and nothing else is known unless given. */
internal fun contextOf(
    members: List<Song>,
    dismissed: List<Song> = emptyList(),
    featured: Map<SongId, List<Long>> = emptyMap(),
    songScores: Map<SongId, Double> = emptyMap(),
    artistScores: Map<ArtistId, Double> = emptyMap(),
): SuggestionContext {
    val artists =
        (members + dismissed).associate { song ->
            song.id to SongArtists(song.artistId, featured[song.id].orEmpty().map(::ArtistId))
        }
    return SuggestionContext(members, dismissed, artists, songScores, artistScores)
}
