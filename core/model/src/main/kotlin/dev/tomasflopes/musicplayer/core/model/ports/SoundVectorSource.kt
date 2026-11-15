package dev.tomasflopes.musicplayer.core.model.ports

import dev.tomasflopes.musicplayer.core.model.sound.SoundSpace

/**
 * The sound of every profiled in-library song as one [SoundSpace], for lists that rank by similarity (ADR 0069).
 * `:core:taste` uses this and never sees a sound profile.
 */
interface SoundVectorSource {
    /**
     * Songs that are in the library and have a profile for the current profiler version, rows in ascending song id.
     * Normalised and weighted already. Built on the first call and kept for the life of the process; later calls
     * return the same instance.
     */
    suspend fun space(): SoundSpace
}
