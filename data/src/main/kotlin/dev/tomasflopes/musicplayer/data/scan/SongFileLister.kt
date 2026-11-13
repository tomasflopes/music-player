package dev.tomasflopes.musicplayer.data.scan

import android.content.ContentResolver
import android.content.ContentUris
import android.provider.MediaStore
import dev.tomasflopes.musicplayer.core.model.ports.MusicRoot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Lists the MP3s inside the music folder. Decision point: an alternative lister (a file walk) can be added
 * beside [MediaStoreSongLister] and both benchmarked (ADR 0005).
 */
interface SongFileLister {
    suspend fun list(root: MusicRoot): List<MediaFile>
}

/** `MediaStore.Audio` filtered to `audio/mpeg` under the root's `RELATIVE_PATH` prefix (ADR 0005). */
class MediaStoreSongLister(
    private val resolver: ContentResolver,
) : SongFileLister {
    override suspend fun list(root: MusicRoot): List<MediaFile> =
        withContext(Dispatchers.IO) {
            val collection = MediaStore.Audio.Media.getContentUri(root.volumeName)
            val location = MusicRootLocation(root.volumeName, root.relativePath)
            val selection =
                "${MediaStore.Audio.Media.MIME_TYPE} = ? AND " +
                    "${MediaStore.Audio.Media.RELATIVE_PATH} LIKE ? ESCAPE '\\'"
            val args = arrayOf(MP3_MIME_TYPE, TreeUriMapper.likePattern(location))
            val files = mutableListOf<MediaFile>()
            resolver.query(collection, COLUMNS, selection, args, null)?.use { cursor ->
                val id = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val relativePath = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.RELATIVE_PATH)
                val name = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                val size = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                val modified = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
                val added = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
                val duration = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                while (cursor.moveToNext()) {
                    val mediaStoreId = cursor.getLong(id)
                    files +=
                        MediaFile(
                            mediaStoreId = mediaStoreId,
                            volumeName = root.volumeName,
                            path = cursor.getString(relativePath).orEmpty() + cursor.getString(name).orEmpty(),
                            sizeBytes = cursor.getLong(size),
                            modifiedEpochSec = cursor.getLong(modified),
                            dateAddedEpochSec = cursor.getLong(added),
                            durationMs = cursor.getLong(duration),
                            contentUri = ContentUris.withAppendedId(collection, mediaStoreId).toString(),
                        )
                }
            }
            files
        }

    private companion object {
        const val MP3_MIME_TYPE = "audio/mpeg"
        val COLUMNS =
            arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.RELATIVE_PATH,
                MediaStore.Audio.Media.DISPLAY_NAME,
                MediaStore.Audio.Media.SIZE,
                MediaStore.Audio.Media.DATE_MODIFIED,
                MediaStore.Audio.Media.DATE_ADDED,
                MediaStore.Audio.Media.DURATION,
            )
    }
}
