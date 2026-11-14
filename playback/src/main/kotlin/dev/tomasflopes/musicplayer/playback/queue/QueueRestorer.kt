package dev.tomasflopes.musicplayer.playback.queue

import dev.tomasflopes.musicplayer.core.model.SongId
import dev.tomasflopes.musicplayer.core.model.ports.SongReader

/**
 * Brings a saved queue in line with the library as it is now (ADR 0036). Songs that left it (retired, ADR 0021) are
 * skipped; nothing else is touched, so the order, the manual entries and the modes come back exactly as saved.
 */
object QueueRestorer {
    /** Songs asked of the library at once; one query takes a bounded number of ids. */
    private const val LOOKUP_CHUNK = 5_000

    /**
     * The saved queue from [store] as it stands in today's library, or `null` when there is nothing to restore: no
     * saved state, a state that cannot be read, a library that cannot be asked, or no song left.
     */
    suspend fun load(
        store: QueueStore,
        songs: SongReader,
    ): SavedQueue? {
        val saved = runCatching { store.load() }.getOrNull() ?: return null
        val inLibrary =
            runCatching {
                val ids =
                    saved.snapshot.entries
                        .map { it.ref.songId }
                        .distinct()
                ids
                    .chunked(LOOKUP_CHUNK)
                    .flatMap { songs.songsByIds(it) }
                    .map { it.id }
                    .toSet()
            }.getOrNull()
        return inLibrary?.let { resolve(saved, it) }
    }

    /**
     * [saved] without the songs that are not [inLibrary], or `null` when no song is left. When the current song is
     * gone the next surviving one takes over (the last one when none follows) and starts from the beginning.
     */
    fun resolve(
        saved: SavedQueue,
        inLibrary: Set<SongId>,
    ): SavedQueue? {
        val snapshot = saved.snapshot
        val kept = snapshot.entries.filter { it.ref.songId in inLibrary }
        if (kept.isEmpty()) return null
        val before = snapshot.entries.take(maxOf(snapshot.index, 0)).count { it.ref.songId in inLibrary }
        val currentKept = snapshot.entries.getOrNull(snapshot.index)?.let { it.ref.songId in inLibrary } == true
        val resolved =
            snapshot.copy(
                entries = kept,
                originalOrder = snapshot.originalOrder.filter { it.songId in inLibrary },
                index = before.coerceAtMost(kept.lastIndex),
            )
        return SavedQueue(resolved, if (currentKept) saved.positionMs else 0L)
    }
}
