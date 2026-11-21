package dev.tomasflopes.musicplayer.core.taste.lists

import dev.tomasflopes.musicplayer.core.model.Song
import dev.tomasflopes.musicplayer.core.model.lists.DynamicListProvider
import dev.tomasflopes.musicplayer.core.model.log.PlaySource
import dev.tomasflopes.musicplayer.core.model.ports.PlayedSongSource
import dev.tomasflopes.musicplayer.core.model.ports.SongReader
import dev.tomasflopes.musicplayer.core.model.ports.SoundVectorSource
import dev.tomasflopes.musicplayer.core.model.sound.SoundSpace
import dev.tomasflopes.musicplayer.core.taste.score.ScoreBoard
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlin.random.Random

/**
 * Songs never played, led by the artists the owner likes and, with [SoundAwareDiscoverPolicy], by songs that sound
 * like the owner's liked songs (design §7.7, ADR 0063, 0071). The work is the [rule]'s; this class feeds it the
 * library, the set of songs the log has heard of, the artist and song scores and the sound space, and re-picks when
 * any of them changes. The same [seed] gives the same random part each time, so the list does not reshuffle while it
 * is open; a new seed (the next app start) gives a different one.
 *
 * [vectors] may be `null` (no sound known): the rule then gets no space and a sound-aware rule falls back to the
 * artist-only list.
 */
class DiscoverProvider(
    private val songs: SongReader,
    private val played: PlayedSongSource,
    private val board: ScoreBoard,
    private val rule: DiscoverRule,
    private val seed: Long,
    private val vectors: SoundVectorSource? = null,
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
            board.observeRanked(),
            space(),
        ) { library, heard, artists, songScores, space ->
            val input = DiscoverInput(library, heard, artists.associate { it.artistId to it.score }, songScores, space)
            rule.pick(input, Random(seed))
        }.distinctUntilChanged()

    /** The space is built once per process (ADR 0069), so asking on every collection is cheap. */
    private fun space(): Flow<SoundSpace?> = flow { emit(vectors?.space()) }

    companion object {
        const val ID = "discover"
    }
}
