package dev.tomasflopes.musicplayer.core.model

/**
 * One MP3 in the library. [title] and [artistName] are what the app shows: the tags as tagged, or the
 * [DisplayFallbacks] when a tag is missing. [artistName] is the file-name artist when that differs from the tag
 * (ADR 0026); [tagArtistName] is the tag artist, the name of [artistId]'s page and the grouping key.
 */
data class Song(
    val id: SongId,
    val title: String,
    val artistId: ArtistId,
    val artistName: String,
    val albumId: AlbumId?,
    val albumTitle: String?,
    val albumArtist: String?,
    val trackNumber: Int?,
    val discNumber: Int?,
    val durationMs: Long,
    val volumeName: String,
    /** Path on the volume, e.g. `Music/HipHop/Ed/01 x.mp3`. */
    val path: String,
    val fileName: String,
    val folderPath: String,
    val dateAddedEpochSec: Long,
    val contentUri: String,
    val hasCover: Boolean,
    val tagArtistName: String = artistName,
)

/** What queues and shuffles carry: small, and enough for "not the same artist twice in a row" later. */
data class SongRef(
    val songId: SongId,
    val mainArtistId: ArtistId,
)

val Song.ref: SongRef get() = SongRef(id, artistId)
