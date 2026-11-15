package dev.tomasflopes.musicplayer.core.taste.lists

import dev.tomasflopes.musicplayer.core.model.Song
import dev.tomasflopes.musicplayer.core.model.lists.DynamicListProvider
import dev.tomasflopes.musicplayer.core.model.log.PlaySource
import dev.tomasflopes.musicplayer.core.model.ports.PlayedSongSource
import dev.tomasflopes.musicplayer.core.model.ports.SongReader
import dev.tomasflopes.musicplayer.core.taste.score.ScoreBoard
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.random.Random

/**
 * Songs never played, led by the artists the owner likes (design §7.7, ADR 0063). The work is [DiscoverPolicy]'s;
 * this class feeds it the library, the set of songs the log has heard of and the artist scores, and re-picks when
 * any of them changes. The same [seed] gives the same random part each time, so the list does not reshuffle while
 * it is open; a new seed (the next app start) gives a different one.
 */
class DiscoverProvider(
    private val songs: SongReader,
    private val played: PlayedSongSource,
    private val board: ScoreBoard,
    private val policy: DiscoverPolicy,
    private val seed: Long,
) : DynamicListProvider {
    override val id = ID
    override val title = "Discover"
    override val order = 20
    override val playSource = PlaySource.DISCOVER

    override fun songs(): Flow<List<Song>> =
        combine(
            songs.observeSongs(),
            played.observePlayedIds(),
            board.observeArtistRanked(),
        ) { library, heard, artists ->
            policy.pick(library, heard, artists.associate { it.artistId to it.score }, Random(seed))
        }.distinctUntilChanged()

    companion object {
        const val ID = "discover"
    }
}
