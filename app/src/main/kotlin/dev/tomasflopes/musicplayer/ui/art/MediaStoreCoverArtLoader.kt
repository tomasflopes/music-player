package dev.tomasflopes.musicplayer.ui.art

import android.content.ContentResolver
import android.graphics.Bitmap
import android.net.Uri
import android.util.LruCache
import android.util.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import dev.tomasflopes.musicplayer.core.model.Song
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Reads the embedded cover through MediaStore's `loadThumbnail` (nothing is downloaded) and keeps the most recent
 * covers in memory, so scrolling a long list does not decode the same album once per row.
 */
class MediaStoreCoverArtLoader(
    private val resolver: ContentResolver,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : CoverArtLoader {
    private val cache =
        object : LruCache<CoverKey, Bitmap>(CACHE_BYTES) {
            override fun sizeOf(
                key: CoverKey,
                value: Bitmap,
            ): Int = value.byteCount
        }

    override suspend fun load(
        song: Song,
        sizePx: Int,
    ): ImageBitmap? {
        val key = song.coverKey(sizePx)
        val cached = cache.get(key) ?: if (song.hasCover) readAndCache(key, song, sizePx) else null
        return cached?.asImageBitmap()
    }

    private suspend fun readAndCache(
        key: CoverKey,
        song: Song,
        sizePx: Int,
    ): Bitmap? = withContext(io) { readThumbnail(song.contentUri, sizePx) }?.also { cache.put(key, it) }

    private fun readThumbnail(
        contentUri: String,
        sizePx: Int,
    ): Bitmap? =
        try {
            resolver.loadThumbnail(Uri.parse(contentUri), Size(sizePx, sizePx), null)
        } catch (ignored: IOException) {
            // A file without a readable cover is normal; the row shows its placeholder.
            null
        }

    private companion object {
        const val CACHE_BYTES = 16 * 1024 * 1024
    }
}
