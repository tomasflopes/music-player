package dev.tomasflopes.musicplayer.core.taste.lists

import dev.tomasflopes.musicplayer.core.model.Song
import dev.tomasflopes.musicplayer.core.model.SongId
import dev.tomasflopes.musicplayer.core.model.lists.DynamicListProvider
import dev.tomasflopes.musicplayer.core.model.log.PlaySource
import dev.tomasflopes.musicplayer.core.model.ports.LibraryChanges
import dev.tomasflopes.musicplayer.core.model.ports.SongReader
import dev.tomasflopes.musicplayer.core.taste.config.TasteConfig
import dev.tomasflopes.musicplayer.core.taste.score.ScoreBoard
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

/**
 * The songs with the best decayed global score (design §7.7), so the list follows current taste instead of
 * all-time counts. Only songs above `top.minScore` are listed and only songs still in the library: a song that
 * left it is skipped and the next one takes its place, and the list is rebuilt when the library changes.
 */
class TopHundredProvider(
    private val board: ScoreBoard,
    private val songs: SongReader,
    private val changes: LibraryChanges,
    private val config: TasteConfig = TasteConfig(),
) : DynamicListProvider {
    override val id = ID
    override val title = "Top ${config.top.size}"
    override val order = 10
    override val playSource = PlaySource.TOP100

    /** Re-checks the library on every score change and on every library change (ADR 0034). */
    override fun songs(): Flow<List<Song>> =
        combine(board.observeRanked(), changes.observe().onStart { emit(Unit) }) { ranked, _ -> ranked }
            .map { ranked -> inLibrary(ranked.filter { it.score > config.top.minScore }.map { it.songId }) }
            .distinctUntilChanged()

    /** Asks the library in chunks of the list size, best first, until the list is full or the ranking runs out. */
    private suspend fun inLibrary(ranked: List<SongId>): List<Song> {
        val size = config.top.size
        val found = ArrayList<Song>(size)
        for (chunk in ranked.chunked(size)) {
            found += songs.songsByIds(chunk)
            if (found.size >= size) break
        }
        return found.take(size)
    }

    companion object {
        const val ID = "top_100"
    }
}
