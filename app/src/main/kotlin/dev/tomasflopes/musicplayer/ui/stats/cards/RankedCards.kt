package dev.tomasflopes.musicplayer.ui.stats.cards

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.tomasflopes.musicplayer.core.model.Song
import dev.tomasflopes.musicplayer.ui.components.AlbumArt
import dev.tomasflopes.musicplayer.ui.components.ArtistAvatar
import dev.tomasflopes.musicplayer.ui.stats.RankedSong
import dev.tomasflopes.musicplayer.ui.stats.WrappedCard
import dev.tomasflopes.musicplayer.ui.stats.WrappedData
import javax.inject.Inject

private fun plays(count: Int) = if (count == 1) "1 play" else "$count plays"

private fun skips(count: Int) = "skipped $count times"

private fun songEntry(
    song: Song,
    trailing: String,
) = RankedEntry(song.title, song.artistName, trailing) { size -> AlbumArt(song, size) }

private fun songEntries(
    ranked: List<RankedSong>,
    trailing: (Int) -> String,
) = ranked.map { songEntry(it.song, trailing(it.count)) }

class TopSongsCard
    @Inject
    constructor() : WrappedCard {
        override val id = "top_songs"
        override val order = 10

        override fun hasContent(data: WrappedData) = data.topSongs.isNotEmpty()

        @Composable
        override fun Content(
            data: WrappedData,
            modifier: Modifier,
        ) = WrappedCardFrame("Top songs", modifier) { RankedList(songEntries(data.topSongs, ::plays)) }
    }

class TopArtistsCard
    @Inject
    constructor() : WrappedCard {
        override val id = "top_artists"
        override val order = 20

        override fun hasContent(data: WrappedData) = data.topArtists.isNotEmpty()

        @Composable
        override fun Content(
            data: WrappedData,
            modifier: Modifier,
        ) = WrappedCardFrame("Top artists", modifier) {
            RankedList(
                data.topArtists.map { artist ->
                    RankedEntry(
                        artist.name,
                        null,
                        plays(artist.count),
                    ) { size -> ArtistAvatar(artist.artistId, artist.name, size) }
                },
            )
        }
    }

class TopAlbumsCard
    @Inject
    constructor() : WrappedCard {
        override val id = "top_albums"
        override val order = 30

        override fun hasContent(data: WrappedData) = data.topAlbums.isNotEmpty()

        @Composable
        override fun Content(
            data: WrappedData,
            modifier: Modifier,
        ) = WrappedCardFrame("Top albums", modifier) {
            RankedList(
                data.topAlbums.map { ranked ->
                    RankedEntry(
                        ranked.album.title,
                        ranked.album.albumArtist,
                        plays(ranked.count),
                    ) { size -> AlbumArt(ranked.cover, size) }
                },
            )
        }
    }

class MostSkippedCard
    @Inject
    constructor() : WrappedCard {
        override val id = "most_skipped"
        override val order = 60

        override fun hasContent(data: WrappedData) = data.mostSkipped.isNotEmpty()

        @Composable
        override fun Content(
            data: WrappedData,
            modifier: Modifier,
        ) = WrappedCardFrame("Most skipped", modifier) { RankedList(songEntries(data.mostSkipped, ::skips)) }
    }
