package dev.tomasflopes.musicplayer.core.taste.shuffle

import dev.tomasflopes.musicplayer.core.model.SongId
import dev.tomasflopes.musicplayer.core.taste.score.ScoreBoard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * The latest global score of every song that has one, readable without waiting. A shuffle is a plain function call
 * on the main thread, so it cannot collect a flow; [BoardGlobalScores] keeps the newest ranking ready instead.
 */
fun interface GlobalScores {
    /** Songs without a score are absent. */
    fun snapshot(): Map<SongId, Double>
}

/** Follows [ScoreBoard.observeRanked] in [scope]. Before the first ranking arrives every song counts as unscored. */
class BoardGlobalScores(
    board: ScoreBoard,
    scope: CoroutineScope,
) : GlobalScores {
    @Volatile private var latest: Map<SongId, Double> = emptyMap()

    init {
        board.observeRanked().onEach { ranked -> latest = ranked.associate { it.songId to it.score } }.launchIn(scope)
    }

    override fun snapshot(): Map<SongId, Double> = latest
}
