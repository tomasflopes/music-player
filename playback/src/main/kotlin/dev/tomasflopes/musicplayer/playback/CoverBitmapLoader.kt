package dev.tomasflopes.musicplayer.playback

import android.graphics.Bitmap
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.google.common.util.concurrent.SettableFuture
import dev.tomasflopes.musicplayer.core.model.SongId
import dev.tomasflopes.musicplayer.core.model.ports.SongReader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.FileNotFoundException

/**
 * The session's bitmap loader. Media3's default one cannot open the `musicplayer://cover/{songId}` URIs the items carry
 * (it hands unknown schemes to an HTTP data source), so the notification, lock screen and car got no artwork. This one
 * answers those URIs from a [CoverBitmapSource] and gives every other request to [delegate] unchanged.
 *
 * Loading runs on [scope], never on the caller's thread; a song with no readable cover fails its future, which Media3
 * treats as "no artwork" (items of songs without a cover carry no artwork URI at all).
 */
@OptIn(UnstableApi::class)
internal class CoverBitmapLoader(
    private val delegate: BitmapLoader,
    private val songs: SongReader,
    private val covers: CoverBitmapSource,
    private val sizePx: Int,
    private val scope: CoroutineScope,
) : BitmapLoader {
    override fun supportsMimeType(mimeType: String): Boolean = delegate.supportsMimeType(mimeType)

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> = delegate.decodeBitmap(data)

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> {
        val songId = CoverUri.songIdOf(uri.toString()) ?: return delegate.loadBitmap(uri)
        val result = SettableFuture.create<Bitmap>()
        val failed = CoroutineExceptionHandler { _, error -> result.setException(error) }
        val job =
            scope.launch(failed) {
                val bitmap = coverOf(songId)
                if (bitmap != null) {
                    result.set(bitmap)
                } else {
                    result.setException(FileNotFoundException("No cover for song ${songId.value}"))
                }
            }
        job.invokeOnCompletion { cause -> if (cause is CancellationException) result.cancel(false) }
        result.addListener({ if (result.isCancelled) job.cancel() }, MoreExecutors.directExecutor())
        return result
    }

    private suspend fun coverOf(songId: SongId): Bitmap? =
        songs.songsByIds(listOf(songId)).firstOrNull()?.let { covers.cover(it, sizePx) }
}
