package dev.tomasflopes.musicplayer.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * The sound profile of one song (ADR 0058, 0065). No foreign key to `song` (as in ADR 0037): a row can outlive a
 * retired song. [features] is the ProfileBlobCodec BLOB; [fileSize] spots a file that was replaced later.
 */
@Entity(tableName = "sound_profile")
data class SoundProfileEntity(
    @PrimaryKey @ColumnInfo(name = "song_id") val songId: Long,
    @ColumnInfo(name = "profiler_version") val profilerVersion: Int,
    val features: ByteArray,
    val source: String,
    @ColumnInfo(name = "file_size") val fileSize: Long,
    @ColumnInfo(name = "analysed_at") val analysedAt: Long,
) {
    override fun equals(other: Any?): Boolean =
        other is SoundProfileEntity &&
            songId == other.songId &&
            profilerVersion == other.profilerVersion &&
            features.contentEquals(other.features) &&
            source == other.source &&
            fileSize == other.fileSize &&
            analysedAt == other.analysedAt

    override fun hashCode(): Int = songId.hashCode()
}

/** A phone analysis that failed for one song and one profiler version (ADR 0058). Three attempts and it stops. */
@Entity(tableName = "analysis_failure", primaryKeys = ["song_id", "profiler_version"])
data class AnalysisFailureEntity(
    @ColumnInfo(name = "song_id") val songId: Long,
    @ColumnInfo(name = "profiler_version") val profilerVersion: Int,
    val attempts: Int,
    @ColumnInfo(name = "last_error") val lastError: String,
)
