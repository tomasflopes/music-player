package dev.tomasflopes.musicplayer.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import dev.tomasflopes.musicplayer.core.model.Song
import dev.tomasflopes.musicplayer.ui.art.LocalCoverArtLoader
import dev.tomasflopes.musicplayer.ui.art.coverKey

/** A song's embedded cover, or a music-note tile when it has none. Decorative: the row's text names the song. */
@Composable
fun AlbumArt(
    song: Song?,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val loader = LocalCoverArtLoader.current
    val sizePx = with(LocalDensity.current) { size.roundToPx() }
    val cover by produceState<ImageBitmap?>(initialValue = null, song?.coverKey(sizePx), loader) {
        value = null
        value = song?.let { loader.load(it, sizePx) }
    }

    Box(
        modifier =
            modifier
                .size(size)
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        val bitmap = cover
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(size / 2),
            )
        }
    }
}
