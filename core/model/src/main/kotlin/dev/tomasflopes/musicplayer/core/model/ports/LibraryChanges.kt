package dev.tomasflopes.musicplayer.core.model.ports

import kotlinx.coroutines.flow.Flow

/**
 * A signal that the songs in the library may have changed (added, retired or returned by a scan), without saying
 * which. Lists that keep songs by id, such as the Top 100, re-check the library when it fires. Implemented by `:data`.
 */
interface LibraryChanges {
    /** Emits when the library's songs may have changed; an emission on collection is allowed but not required. */
    fun observe(): Flow<Unit>
}
