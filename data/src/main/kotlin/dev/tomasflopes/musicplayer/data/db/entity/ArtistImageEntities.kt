package dev.tomasflopes.musicplayer.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * What the owner chose for one artist's picture (ADR 0030). No row means automatic. [kind] is `remote` (a Deezer
 * URL in [value]), `local` (a file name in app storage) or `generated` (no value).
 */
@Entity(tableName = "artist_image_override")
data class ArtistImageOverrideEntity(
    @PrimaryKey @ColumnInfo(name = "artist_id") val artistId: Long,
    val kind: String,
    val value: String?,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

/** The last Deezer answer for one artist. A null [pictureUrl] means it found nothing. */
@Entity(tableName = "artist_image_lookup")
data class ArtistImageLookupEntity(
    @PrimaryKey @ColumnInfo(name = "artist_id") val artistId: Long,
    @ColumnInfo(name = "picture_url") val pictureUrl: String?,
    @ColumnInfo(name = "checked_at") val checkedAt: Long,
)
