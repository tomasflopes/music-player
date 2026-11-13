package dev.tomasflopes.musicplayer.playback

import android.graphics.Bitmap
import dev.tomasflopes.musicplayer.core.model.Song

/**
 * Where the media session gets a song's cover for the notification, lock screen and car. `:playback` must not depend
 * on `:app` (ADR 0013), where covers are read, so the app binds the implementation with Hilt.
 */
interface CoverBitmapSource {
    /**
     * The embedded cover scaled to about [sizePx] square, or `null` when the song has none or it cannot be read.
     * Suspends off the main thread and is expected to cache, since skipping tracks asks again and again.
     */
    suspend fun cover(
        song: Song,
        sizePx: Int,
    ): Bitmap?
}
