package dev.tomasflopes.musicplayer.ui.art

import android.content.Context
import android.net.Uri
import dev.tomasflopes.musicplayer.core.artistimage.LocalImageFiles
import dev.tomasflopes.musicplayer.core.model.ArtistId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.time.Clock

/**
 * Gallery pictures for artists, copied once into the app's own storage (ADR 0030) so no permission on the original
 * is kept. Every copy gets a new name, so an image cache never serves a picture that was replaced.
 */
class GalleryImageStore(
    private val context: Context,
    private val clock: Clock,
) : LocalImageFiles {
    private val directory = File(context.filesDir, DIRECTORY)

    fun file(fileName: String): File = File(directory, fileName)

    /** The new file's name, or `null` when the picture could not be read or written. */
    @Suppress("SwallowedException")
    suspend fun copy(
        artistId: ArtistId,
        source: Uri,
    ): String? =
        withContext(Dispatchers.IO) {
            val name = "${artistId.value}-${clock.millis()}.img"
            try {
                directory.mkdirs()
                val input = context.contentResolver.openInputStream(source)
                input?.use { stream -> file(name).outputStream().use { stream.copyTo(it) } }
                if (input == null) null else name
            } catch (e: IOException) {
                file(name).delete()
                null
            }
        }

    override suspend fun delete(fileName: String) {
        // Only names this class made: never a path that could leave the folder.
        if (File(fileName).name == fileName) withContext(Dispatchers.IO) { file(fileName).delete() }
    }

    private companion object {
        const val DIRECTORY = "artist_images"
    }
}
