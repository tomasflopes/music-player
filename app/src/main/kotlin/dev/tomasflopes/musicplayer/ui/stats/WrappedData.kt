package dev.tomasflopes.musicplayer.ui.stats

import dev.tomasflopes.musicplayer.core.model.Album
import dev.tomasflopes.musicplayer.core.model.ArtistId
import dev.tomasflopes.musicplayer.core.model.Song
import dev.tomasflopes.musicplayer.core.taste.stats.PeriodStats

/** A library song with a count: plays in a top list, skips in the most-skipped list. */
data class RankedSong(
    val song: Song,
    val count: Int,
)

data class RankedArtist(
    val artistId: ArtistId,
    val name: String,
    val count: Int,
)

/** [cover] is the song whose embedded cover stands for the album, when there is one. */
data class RankedAlbum(
    val album: Album,
    val cover: Song?,
    val count: Int,
)

/** [artist] holds the plays of this period; [previousPlays] is what it had in the one before. */
data class ComebackData(
    val artist: RankedArtist,
    val previousPlays: Int,
)

/** What the cards of one period draw: the numbers of [PeriodStats] and the library names behind its ids. */
data class WrappedData(
    val stats: PeriodStats,
    val topSongs: List<RankedSong>,
    val topArtists: List<RankedArtist>,
    val topAlbums: List<RankedAlbum>,
    val mostSkipped: List<RankedSong>,
    val discoveries: List<Song>,
    val comeback: ComebackData?,
) {
    val isEmpty: Boolean get() = stats.isEmpty
}

sealed interface WrappedState {
    data object Loading : WrappedState

    data class Ready(
        val data: WrappedData,
    ) : WrappedState
}
