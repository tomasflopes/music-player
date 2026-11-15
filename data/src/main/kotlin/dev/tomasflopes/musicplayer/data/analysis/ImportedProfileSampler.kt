package dev.tomasflopes.musicplayer.data.analysis

import dev.tomasflopes.musicplayer.core.analysis.ProfileBlobCodec
import dev.tomasflopes.musicplayer.core.analysis.SoundProfile
import dev.tomasflopes.musicplayer.core.analysis.SoundProfiler
import dev.tomasflopes.musicplayer.data.db.MusicDatabase
import dev.tomasflopes.musicplayer.data.db.entity.mediaStoreContentUri
import javax.inject.Inject

/** A song whose profile came from the Mac import: where to decode it again, and what the Mac stored. */
class ImportedProfile(
    val location: String,
    val stored: SoundProfile,
)

/** Picks imported songs at random for the debug consistency report (ADR 0060). */
class ImportedProfileSampler
    @Inject
    constructor(
        private val db: MusicDatabase,
        private val profiler: SoundProfiler,
    ) {
        suspend fun sample(limit: Int): List<ImportedProfile> =
            db.soundProfileDao().importedSample(profiler.version, limit).map {
                ImportedProfile(
                    mediaStoreContentUri(it.volumeName, it.mediaStoreId),
                    ProfileBlobCodec.decode(it.features),
                )
            }
    }
