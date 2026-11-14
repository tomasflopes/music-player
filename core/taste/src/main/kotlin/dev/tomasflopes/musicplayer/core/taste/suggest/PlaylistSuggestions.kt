package dev.tomasflopes.musicplayer.core.taste.suggest

import dev.tomasflopes.musicplayer.core.model.ArtistId
import dev.tomasflopes.musicplayer.core.model.Song
import dev.tomasflopes.musicplayer.core.model.SongArtists
import dev.tomasflopes.musicplayer.core.model.SongId
import dev.tomasflopes.musicplayer.core.model.playlists.PlaylistDismissals
import dev.tomasflopes.musicplayer.core.model.playlists.PlaylistId
import dev.tomasflopes.musicplayer.core.model.playlists.PlaylistReader
import dev.tomasflopes.musicplayer.core.model.ports.SongArtistSource
import dev.tomasflopes.musicplayer.core.model.ports.SongReader
import dev.tomasflopes.musicplayer.core.taste.config.SuggestionConfig
import dev.tomasflopes.musicplayer.core.taste.score.ScoreBoard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlin.coroutines.CoroutineContext

/** Where a suggestion block is shown; each place says how many playlist songs it wants first. */
enum class SuggestionPlace { PLAYLIST_PAGE, NEW_PLAYLIST_PICKER }

/**
 * About ten songs from the library for one playlist (design 7.13, ADR 0051): not in the playlist, not dismissed for
 * it, and ranked by [SuggestionRanker]. Emits again when the playlist, its dismissals or the library change, so a "+"
 * or "✕" refreshes the list at once. Taste scores are read once per collection, so the list does not move while the
 * owner listens; opening the block again picks up newer scores. The work runs on [computeOn] because it looks at
 * every library song.
 */
class PlaylistSuggestions(
    private val playlists: PlaylistReader,
    private val dismissals: PlaylistDismissals,
    private val library: SongReader,
    private val artists: SongArtistSource,
    private val board: ScoreBoard,
    private val ranker: SuggestionRanker,
    private val config: SuggestionConfig = SuggestionConfig(),
    private val computeOn: CoroutineContext = Dispatchers.Default,
) {
    private class Scores(
        val songs: Map<SongId, Double>,
        val artists: Map<ArtistId, Double>,
    )

    private class Inputs(
        val members: List<Song>,
        val dismissed: Set<SongId>,
        val library: List<Song>,
    )

    fun observe(
        id: PlaylistId,
        place: SuggestionPlace,
    ): Flow<List<Song>> =
        flow {
            val scores = readScores()
            // Artists are looked up once per song for as long as the block is collected.
            val known = HashMap<SongId, SongArtists>()
            val minimum = minimum(place)
            emitAll(inputs(id).map { suggest(it, scores, known, minimum) })
        }.distinctUntilChanged().flowOn(computeOn)

    private fun inputs(id: PlaylistId): Flow<Inputs> =
        combine(playlists.observeSongs(id), dismissals.observe(id), library.observeSongs()) { members, dismissed, all ->
            Inputs(members, dismissed, all)
        }

    private fun minimum(place: SuggestionPlace): Int =
        when (place) {
            SuggestionPlace.PLAYLIST_PAGE -> config.minSongsOnPage
            SuggestionPlace.NEW_PLAYLIST_PICKER -> config.minSongsInPicker
        }

    private suspend fun readScores(): Scores =
        Scores(
            songs = board.observeRanked().first().associate { it.songId to it.score },
            artists = board.observeArtistRanked().first().associate { it.artistId to it.score },
        )

    private suspend fun suggest(
        inputs: Inputs,
        scores: Scores,
        known: MutableMap<SongId, SongArtists>,
        minimum: Int,
    ): List<Song> {
        if (inputs.members.size < minimum) return emptyList()
        known += artists.artistsOf(inputs.library.map { it.id }.filter { it !in known })
        val taken = inputs.members.mapTo(HashSet()) { it.id } + inputs.dismissed
        val context =
            SuggestionContext(
                members = inputs.members,
                dismissed = inputs.library.filter { it.id in inputs.dismissed },
                artists = known,
                songScores = scores.songs,
                artistScores = scores.artists,
            )
        return ranker.rank(inputs.library.filter { it.id !in taken }, context, config.size)
    }
}
