package dev.tomasflopes.musicplayer.data.db.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import dev.tomasflopes.musicplayer.core.analysis.SoundProfile
import dev.tomasflopes.musicplayer.data.db.entity.AnalysisFailureEntity
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

/** A song the phone still has to analyse (ADR 0058, 0059). [fileSize] is the song row's. */
data class QueuedSongRow(
    val id: Long,
    @ColumnInfo(name = "media_store_id") val mediaStoreId: Long,
    @ColumnInfo(name = "volume_name") val volumeName: String,
    @ColumnInfo(name = "file_size") val fileSize: Long,
    val attempts: Int,
)

/** Settings progress: analysed songs, songs the phone will still try, and songs it gave up on. */
data class AnalysisProgressRow(
    val profiled: Int,
    val waiting: Int,
    @ColumnInfo(name = "gave_up") val gaveUp: Int,
)

/** An imported (Mac) profile and where to find the song again, for the debug consistency report (ADR 0060). */
class ImportedProfileRow(
    @ColumnInfo(name = "song_id") val songId: Long,
    @ColumnInfo(name = "media_store_id") val mediaStoreId: Long,
    @ColumnInfo(name = "volume_name") val volumeName: String,
    val features: ByteArray,
)

/** The first 33 floats of a profile BLOB, which is all the sound space needs (ADR 0069). */
class SoundHeadRow(
    @ColumnInfo(name = "song_id") val songId: Long,
    val head: ByteArray,
)

/** Bytes of the fixed part of a profile (33 float32, ADR 0065). */
private const val HEAD_BYTES = SoundProfile.FIXED_SIZE * 4

/** The "no profile for this file and version" join shared by the queue queries (ADR 0058). */
private const val QUEUE_FROM =
    "FROM song " +
        "LEFT JOIN sound_profile ON sound_profile.song_id = song.id " +
        "AND sound_profile.profiler_version = :profilerVersion AND sound_profile.file_size = song.file_size " +
        "LEFT JOIN analysis_failure ON analysis_failure.song_id = song.id " +
        "AND analysis_failure.profiler_version = :profilerVersion "

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

    /**
     * The phone queue (ADR 0058): in-library songs with no profile for the current version and file, and fewer than
     * [maxAttempts] failures, newest first, leaving out the ids in [skip].
     */
    @Query(
        "SELECT song.id, song.media_store_id, song.volume_name, song.file_size, " +
            "COALESCE(analysis_failure.attempts, 0) AS attempts " + QUEUE_FROM +
            "WHERE song.in_library = 1 AND sound_profile.song_id IS NULL " +
            "AND COALESCE(analysis_failure.attempts, 0) < :maxAttempts AND song.id NOT IN (:skip) " +
            "ORDER BY song.date_added DESC, song.id LIMIT :limit",
    )
    suspend fun nextQueued(
        profilerVersion: Int,
        maxAttempts: Int,
        skip: List<Long>,
        limit: Int,
    ): List<QueuedSongRow>

    @Query(
        "SELECT COUNT(*) " + QUEUE_FROM +
            "WHERE song.in_library = 1 AND sound_profile.song_id IS NULL " +
            "AND COALESCE(analysis_failure.attempts, 0) < :maxAttempts",
    )
    suspend fun countWaiting(
        profilerVersion: Int,
        maxAttempts: Int,
    ): Int

    @Query(
        "SELECT " +
            "COALESCE(SUM(CASE WHEN sound_profile.song_id IS NOT NULL THEN 1 ELSE 0 END), 0) AS profiled, " +
            "COALESCE(SUM(CASE WHEN sound_profile.song_id IS NULL " +
            "AND COALESCE(analysis_failure.attempts, 0) < :maxAttempts THEN 1 ELSE 0 END), 0) AS waiting, " +
            "COALESCE(SUM(CASE WHEN sound_profile.song_id IS NULL " +
            "AND COALESCE(analysis_failure.attempts, 0) >= :maxAttempts THEN 1 ELSE 0 END), 0) AS gave_up " +
            QUEUE_FROM + "WHERE song.in_library = 1",
    )
    fun observeProgress(
        profilerVersion: Int,
        maxAttempts: Int,
    ): Flow<AnalysisProgressRow>

    @Query("SELECT attempts FROM analysis_failure WHERE song_id = :songId AND profiler_version = :profilerVersion")
    suspend fun failedAttempts(
        songId: Long,
        profilerVersion: Int,
    ): Int?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFailure(failure: AnalysisFailureEntity)

    @Query("DELETE FROM analysis_failure WHERE song_id = :songId")
    suspend fun deleteFailures(songId: Long)

    /** Up to [limit] in-library songs whose current profile came from the Mac import, picked at random. */
    @Query(
        "SELECT sound_profile.song_id, song.media_store_id, song.volume_name, sound_profile.features " +
            "FROM sound_profile JOIN song ON song.id = sound_profile.song_id " +
            "WHERE song.in_library = 1 AND sound_profile.source = 'mac' " +
            "AND sound_profile.profiler_version = :profilerVersion AND sound_profile.file_size = song.file_size " +
            "ORDER BY RANDOM() LIMIT :limit",
    )
    suspend fun importedSample(
        profilerVersion: Int,
        limit: Int,
    ): List<ImportedProfileRow>

    /**
     * The head of every current profile of an in-library song (ADR 0058, 0069): the version is the current one, the
     * file is the one it was made from, and the BLOB is long enough to hold the fixed part. Ascending song id.
     */
    @Query(
        "SELECT sound_profile.song_id AS song_id, substr(sound_profile.features, 1, $HEAD_BYTES) AS head " +
            "FROM sound_profile JOIN song ON song.id = sound_profile.song_id " +
            "WHERE song.in_library = 1 AND sound_profile.profiler_version = :profilerVersion " +
            "AND sound_profile.file_size = song.file_size AND length(sound_profile.features) >= $HEAD_BYTES " +
            "ORDER BY sound_profile.song_id",
    )
    suspend fun soundHeads(profilerVersion: Int): List<SoundHeadRow>

    /**
     * The head of the current profile of one in-library song, or null (ADR 0058, 0092): same rules as [soundHeads]
     * (current version, the file it was made from, a BLOB long enough for the fixed part).
     */
    @Query(
        "SELECT substr(sound_profile.features, 1, $HEAD_BYTES) " +
            "FROM sound_profile JOIN song ON song.id = sound_profile.song_id " +
            "WHERE song.id = :songId AND song.in_library = 1 AND sound_profile.profiler_version = :profilerVersion " +
            "AND sound_profile.file_size = song.file_size AND length(sound_profile.features) >= $HEAD_BYTES",
    )
    suspend fun soundHead(
        songId: Long,
        profilerVersion: Int,
    ): ByteArray?
}
