package dev.tomasflopes.musicplayer.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A saved mix (design 7.6, ADR 0076). Its learned state is not stored: it is a function of the log (no score
 * table).
 */
@Entity(tableName = "mix")
data class MixEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

/**
 * One seed of a mix: [kind] is `song` or `artist` and [refId] the song or artist id. A seed is in a mix once (the
 * primary key). [refId] has no foreign key: songs are never deleted (ADR 0021) and a retired song keeps its seed row,
 * which readers hide until the file returns (as `playlist_song`, ADR 0037). [position] orders the seeds as picked.
 */
@Entity(
    tableName = "mix_seed",
    primaryKeys = ["mix_id", "kind", "ref_id"],
    foreignKeys = [
        ForeignKey(
            entity = MixEntity::class,
            parentColumns = ["id"],
            childColumns = ["mix_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["mix_id", "position"])],
)
data class MixSeedEntity(
    @ColumnInfo(name = "mix_id") val mixId: Long,
    val position: Int,
    val kind: String,
    @ColumnInfo(name = "ref_id") val refId: Long,
)
