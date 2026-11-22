package dev.tomasflopes.musicplayer.ui.stats

import dev.tomasflopes.musicplayer.core.model.AlbumId
import dev.tomasflopes.musicplayer.core.model.ArtistId
import dev.tomasflopes.musicplayer.core.model.ports.AlbumReader
import dev.tomasflopes.musicplayer.core.model.ports.ArtistReader
import dev.tomasflopes.musicplayer.core.model.ports.SongReader
import dev.tomasflopes.musicplayer.core.taste.stats.PeriodStats
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Turns the ids of a [PeriodStats] into names and covers. It asks for the few ids a card shows (a handful of songs,
 * artists and albums), never for the library. Something no longer in the library, such as a retired song, is left
 * out and the others keep their order.
 */
class WrappedDataResolver
    @Inject
    constructor(
        private val songs: SongReader,
        private val artists: ArtistReader,
        private val albums: AlbumReader,
    ) {
        suspend fun resolve(stats: PeriodStats): WrappedData {
            val albumsById = albumsOf(stats.topAlbums.map { it.albumId })
            val songIds =
                (
                    stats.topSongs.map { it.songId } + stats.mostSkipped.map { it.songId } +
                        stats.newDiscoveries.first.map { it.songId } + albumsById.values.mapNotNull { it.coverSongId }
                ).distinct()
            val songsById = songs.songsByIds(songIds).associateBy { it.id }
            val artistsById = artistsOf(listOfNotNull(stats.comeback?.artistId) + stats.topArtists.map { it.artistId })
            return WrappedData(
                stats = stats,
                topSongs =
                    stats.topSongs.mapNotNull { plays ->
                        songsById[plays.songId]?.let { RankedSong(it, plays.plays) }
                    },
                topArtists =
                    stats.topArtists.mapNotNull { plays ->
                        artistsById[plays.artistId]?.let { RankedArtist(plays.artistId, it, plays.plays) }
                    },
                topAlbums =
                    stats.topAlbums.mapNotNull { plays ->
                        albumsById[plays.albumId]?.let {
                            RankedAlbum(
                                it,
                                it.coverSongId?.let(songsById::get),
                                plays.plays,
                            )
                        }
                    },
                mostSkipped =
                    stats.mostSkipped.mapNotNull { skips ->
                        songsById[skips.songId]?.let { RankedSong(it, skips.skips) }
                    },
                discoveries = stats.newDiscoveries.first.mapNotNull { songsById[it.songId] },
                comeback =
                    stats.comeback?.let { comeback ->
                        artistsById[comeback.artistId]?.let {
                            ComebackData(RankedArtist(comeback.artistId, it, comeback.plays), comeback.previousPlays)
                        }
                    },
            )
        }

        private suspend fun artistsOf(ids: List<ArtistId>): Map<ArtistId, String> =
            ids.distinct().mapNotNull { id -> artists.observeArtist(id).first()?.let { id to it.name } }.toMap()

        private suspend fun albumsOf(ids: List<AlbumId>) =
            ids.distinct().mapNotNull { id -> albums.observeAlbum(id).first() }.associateBy { it.id }
    }
