package dev.tomasflopes.musicplayer.data.analysis

import dev.tomasflopes.musicplayer.core.analysis.SoundProfiler
import dev.tomasflopes.musicplayer.core.analysis.TransitionProfiles
import dev.tomasflopes.musicplayer.core.model.transition.TransitionProfile
import dev.tomasflopes.musicplayer.core.model.transition.TransitionProfileSource
import dev.tomasflopes.musicplayer.data.db.MusicDatabase
import javax.inject.Inject

/**
 * The transition data of one song over Room (ADR 0092): one query for the whole BLOB of its current profile (the
 * fixed part and the energy curve, ADR 0065, 0104; about 1 KB), decoded into a [TransitionProfile]. No decoding of
 * audio, no schema change; a song without a current profile, or with numbers that cannot be a beat grid, has none.
 */
class RoomTransitionProfileSource
    @Inject
    constructor(
        private val db: MusicDatabase,
        private val profiler: SoundProfiler,
    ) : TransitionProfileSource {
        override suspend fun profile(songId: Long): TransitionProfile? {
            val blob = db.soundProfileDao().soundBlob(songId, profiler.version) ?: return null
            return TransitionProfiles.fromBlob(blob)
        }
    }
