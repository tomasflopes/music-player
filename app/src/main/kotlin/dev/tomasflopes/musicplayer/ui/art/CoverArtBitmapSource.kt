package dev.tomasflopes.musicplayer.ui.art

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import dev.tomasflopes.musicplayer.core.model.Song
import dev.tomasflopes.musicplayer.playback.CoverBitmapSource

/**
 * The media session's covers come from the same [CoverArtLoader] the screens use, so one read and one in-memory cache
 * serve both: skipping tracks of an album does not decode its cover again, and nothing here is downloaded.
 */
class CoverArtBitmapSource(
    private val loader: CoverArtLoader,
) : CoverBitmapSource {
    override suspend fun cover(
        song: Song,
        sizePx: Int,
    ): Bitmap? = loader.load(song, sizePx)?.asAndroidBitmap()
}
