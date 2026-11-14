package dev.tomasflopes.musicplayer.ui.art

import androidx.compose.runtime.staticCompositionLocalOf
import coil3.ImageLoader
import dev.tomasflopes.musicplayer.core.artistimage.ArtistImageResolver
import java.io.File

/** What the artist avatars need: who answers, how photos are downloaded and where gallery picks live. */
class ArtistImageHost(
    val resolver: ArtistImageResolver,
    val imageLoader: ImageLoader,
    private val gallery: GalleryImageStore,
) {
    fun file(fileName: String): File = gallery.file(fileName)
}

/** `null` in previews and tests: avatars then show the initials tile and never touch the network. */
val LocalArtistImageHost = staticCompositionLocalOf<ArtistImageHost?> { null }
