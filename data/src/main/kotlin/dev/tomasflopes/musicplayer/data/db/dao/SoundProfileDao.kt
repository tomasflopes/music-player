package dev.tomasflopes.musicplayer.data.db.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import dev.tomasflopes.musicplayer.data.db.entity.SoundProfileEntity
import kotlinx.coroutines.flow.Flow

/** What the import matcher needs about every song (ADR 0057), retired ones included. */
data class ImportTargetRow(
    val id: Long,
    val path: String,
    @ColumnInfo(name = "file_size") val fileSize: Long,
    @ColumnInfo(name = "in_library") val inLibrary: Boolean,
)

/** The Settings summary: songs in the library with a profile for the current file, and the ones still queued. */
data class AnalysisSummaryRow(
    val profiled: Int,
    val queued: Int,
)

@Dao
interface SoundProfileDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: SoundProfileEntity)

    @Query("SELECT id, path, file_size, in_library FROM song")
    suspend fun importTargets(): List<ImportTargetRow>

    /**
     * A profile counts only for the file it was made from (same size) and the current profiler version. Every
     * in-library song is either profiled or queued, so profiled + queued is the library size (ADR 0058).
     */
    @Query(
        "SELECT COALESCE(SUM(CASE WHEN sound_profile.song_id IS NULL THEN 0 ELSE 1 END), 0) AS profiled, " +
            "COALESCE(SUM(CASE WHEN sound_profile.song_id IS NULL THEN 1 ELSE 0 END), 0) AS queued " +
            "FROM song LEFT JOIN sound_profile ON sound_profile.song_id = song.id " +
            "AND sound_profile.profiler_version = :profilerVersion AND sound_profile.file_size = song.file_size " +
            "WHERE song.in_library = 1",
    )
    fun observeSummary(profilerVersion: Int): Flow<AnalysisSummaryRow>
}
