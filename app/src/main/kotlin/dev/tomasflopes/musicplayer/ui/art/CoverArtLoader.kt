package dev.tomasflopes.musicplayer.ui.art

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.ImageBitmap
import dev.tomasflopes.musicplayer.core.model.Song

/** Loads the cover embedded in a song's file. The one place that knows how covers are read from the phone. */
interface CoverArtLoader {
    /** The cover scaled to about [sizePx] square, or `null` when the song has none or it cannot be read. */
    suspend fun load(
        song: Song,
        sizePx: Int,
    ): ImageBitmap?
}

/** What previews and tests get until a real loader is provided: no song has a cover. */
object NoCoverArt : CoverArtLoader {
    override suspend fun load(
        song: Song,
        sizePx: Int,
    ): ImageBitmap? = null
}

val LocalCoverArtLoader = staticCompositionLocalOf<CoverArtLoader> { NoCoverArt }

internal enum class CoverOwner { ALBUM, SONG }

/** Cache key for a cover: songs of one album share one entry, a song without an album is keyed by itself. */
internal data class CoverKey(
    val owner: CoverOwner,
    val id: Long,
    val sizePx: Int,
)

internal fun Song.coverKey(sizePx: Int): CoverKey =
    albumId?.let { CoverKey(CoverOwner.ALBUM, it.value, sizePx) } ?: CoverKey(CoverOwner.SONG, id.value, sizePx)
