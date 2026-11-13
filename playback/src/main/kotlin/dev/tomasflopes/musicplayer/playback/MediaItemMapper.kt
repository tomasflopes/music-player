package dev.tomasflopes.musicplayer.playback

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import dev.tomasflopes.musicplayer.core.model.Song
import dev.tomasflopes.musicplayer.core.model.SongId
import dev.tomasflopes.musicplayer.playback.queue.QueueEntry

/** What goes into a Media3 item, as plain strings so the mapping is testable without Android. */
data class ItemFields(
    val mediaId: String,
    val uri: String,
    val title: String,
    val artist: String,
    val albumTitle: String?,
    val artworkUri: String?,
)

/** Turns a queue entry plus its song into the item the player and the notification show. Tag text is never altered. */
class MediaItemMapper {
    @Suppress("UnusedParameter")
    fun fieldsOf(
        entry: QueueEntry,
        song: Song,
    ) = ItemFields(
        mediaId = song.id.value.toString(),
        uri = song.contentUri,
        title = song.title,
        artist = song.artistName,
        albumTitle = song.albumTitle,
        artworkUri = if (song.hasCover) "$COVER_SCHEME://cover/${song.id.value}" else null,
    )

    fun toMediaItem(
        entry: QueueEntry,
        song: Song,
    ): MediaItem {
        val fields = fieldsOf(entry, song)
        val metadata =
            MediaMetadata
                .Builder()
                .setTitle(fields.title)
                .setArtist(fields.artist)
                .setAlbumTitle(fields.albumTitle)
                .setArtworkUri(fields.artworkUri?.let(Uri::parse))
                .build()
        return MediaItem
            .Builder()
            .setMediaId(fields.mediaId)
            .setUri(fields.uri)
            .setMediaMetadata(metadata)
            .build()
    }

    fun songIdOf(mediaId: String): SongId? = mediaId.toLongOrNull()?.let(::SongId)

    private companion object {
        const val COVER_SCHEME = "musicplayer"
    }
}
