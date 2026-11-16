package dev.tomasflopes.musicplayer.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import dev.tomasflopes.musicplayer.data.db.entity.MixEntity
import dev.tomasflopes.musicplayer.data.db.entity.MixSeedEntity
import kotlinx.coroutines.flow.Flow

/** A seed the owner can see: an artist seed, or a song seed whose song is in the library (ADR 0021, 0037). */
private const val VISIBLE_SEEDS =
    "SELECT mix_seed.* FROM mix_seed LEFT JOIN song ON mix_seed.kind = 'song' AND song.id = mix_seed.ref_id " +
        "WHERE (mix_seed.kind <> 'song' OR song.in_library = 1)"

/** Mixes and their seeds. */
@Dao
interface MixDao {
    @Insert
    suspend fun insert(mix: MixEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSeeds(rows: List<MixSeedEntity>)

    @Query("UPDATE mix SET name = :name, updated_at = :now WHERE id = :id")
    suspend fun rename(
        id: Long,
        name: String,
        now: Long,
    )

    @Query("UPDATE mix SET updated_at = :now WHERE id = :id")
    suspend fun touch(
        id: Long,
        now: Long,
    )

    /** The seeds go with it (cascade). The listening log keeps its `mix_id` values. */
    @Query("DELETE FROM mix WHERE id = :id")
    suspend fun delete(id: Long)

    /** Removes the seeds the owner can see; hidden seeds (retired songs) keep their rows. */
    @Query(
        "DELETE FROM mix_seed WHERE mix_id = :id AND (kind <> 'song' OR " +
            "ref_id IN (SELECT id FROM song WHERE in_library = 1))",
    )
    suspend fun deleteVisibleSeeds(id: Long)

    @Query("SELECT * FROM mix ORDER BY created_at, id")
    fun observeAll(): Flow<List<MixEntity>>

    @Query("SELECT * FROM mix WHERE id = :id")
    fun observe(id: Long): Flow<MixEntity?>

    @Query("SELECT * FROM mix WHERE id = :id")
    suspend fun get(id: Long): MixEntity?

    @Query("$VISIBLE_SEEDS ORDER BY mix_seed.mix_id, mix_seed.position")
    fun observeVisibleSeeds(): Flow<List<MixSeedEntity>>

    @Query("$VISIBLE_SEEDS AND mix_seed.mix_id = :id ORDER BY mix_seed.position")
    suspend fun visibleSeeds(id: Long): List<MixSeedEntity>

    /** Every row, hidden seeds included. */
    @Query("SELECT COUNT(*) FROM mix_seed WHERE mix_id = :id")
    suspend fun rawSeedCount(id: Long): Int
}
