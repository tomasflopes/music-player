package dev.tomasflopes.musicplayer.playback

import dev.tomasflopes.musicplayer.core.model.Song
import dev.tomasflopes.musicplayer.core.model.SongId
import dev.tomasflopes.musicplayer.playback.queue.QueueEntry

/** The songs after the current one, and how many of the first ones were queued by hand. */
internal data class Upcoming(
    val songs: List<Song>,
    val manualCount: Int,
)

/**
 * [ahead] is the queue after the current song. A song missing from [loaded] (removed from the library) is dropped
 * and not counted, so [Upcoming.manualCount] always matches [Upcoming.songs]. Manual entries are a leading run
 * (ADR 0010), so only that run is counted.
 */
internal fun upcomingOf(
    ahead: List<QueueEntry>,
    loaded: Map<SongId, Song>,
): Upcoming {
    val known = ahead.mapNotNull { entry -> loaded[entry.ref.songId]?.let { song -> entry to song } }
    return Upcoming(
        songs = known.map { (_, song) -> song },
        manualCount = known.takeWhile { (entry, _) -> entry.manual }.size,
    )
}
