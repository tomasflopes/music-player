package dev.tomasflopes.musicplayer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import coil3.compose.AsyncImage
import dev.tomasflopes.musicplayer.core.artistimage.ArtistImage
import dev.tomasflopes.musicplayer.core.artistimage.ArtistRef
import dev.tomasflopes.musicplayer.core.artistimage.initialsOf
import dev.tomasflopes.musicplayer.core.model.ArtistId
import dev.tomasflopes.musicplayer.core.model.Song
import dev.tomasflopes.musicplayer.ui.art.ArtistImageHost
import dev.tomasflopes.musicplayer.ui.art.LocalArtistImageHost

private const val INITIALS_TEXT_RATIO = 0.4f

/**
 * A round picture for an artist: the owner's choice, a Deezer photo, the album covers or letters on an orange tile
 * (ADR 0029). It starts as the letters and upgrades once the answer is in, so a row never waits for the network.
 * Decorative: the row's text names the artist.
 */
@Composable
fun ArtistAvatar(
    artistId: ArtistId,
    name: String,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val host = LocalArtistImageHost.current
    Box(
        modifier =
            modifier
                .size(size)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        when (val image = rememberArtistImage(host, artistId, name)) {
            is ArtistImage.Initials -> InitialsTile(image.text, size)
            is ArtistImage.Collage -> CollageTile(image.covers, size)
            is ArtistImage.Remote -> Photo(host, image.url, name, size)
            is ArtistImage.LocalFile -> Photo(host, host?.file(image.fileName), name, size)
        }
    }
}

/** Asks only while the row is on screen: the lookup starts with the composition and is shared per artist. */
@Composable
private fun rememberArtistImage(
    host: ArtistImageHost?,
    artistId: ArtistId,
    name: String,
): ArtistImage {
    val initials = remember(name) { ArtistImage.Initials(initialsOf(name)) }
    if (host == null) return initials
    val answers by host.resolver.changes.collectAsState()
    val image by produceState<ArtistImage>(initials, host, artistId, name, answers) {
        value = host.resolver.peek(artistId) ?: initials
        value = host.resolver.resolve(ArtistRef(artistId, name))
    }
    return image
}

@Composable
private fun InitialsTile(
    text: String,
    size: Dp,
) {
    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = with(LocalDensity.current) { (size * INITIALS_TEXT_RATIO).toSp() },
        )
    }
}

@Composable
private fun CollageTile(
    covers: List<Song>,
    size: Dp,
) {
    if (covers.size == 1) {
        AlbumArt(covers.first(), size)
    } else {
        Column {
            covers.chunked(2).forEach { pair ->
                Row { pair.forEach { AlbumArt(it, size / 2) } }
            }
        }
    }
}

/** The photo over the letters: while it loads, or if it cannot be loaded, the letters stay. */
@Composable
private fun Photo(
    host: ArtistImageHost?,
    model: Any?,
    name: String,
    size: Dp,
) {
    InitialsTile(initialsOf(name), size)
    if (host != null && model != null) RemotePhoto(host, model, Modifier.fillMaxSize())
}

/** An image from the web or from app storage, drawn with the loader that has the photo disk cache. */
@Composable
internal fun RemotePhoto(
    host: ArtistImageHost,
    model: Any,
    modifier: Modifier = Modifier,
) {
    AsyncImage(
        model = model,
        contentDescription = null,
        imageLoader = host.imageLoader,
        contentScale = ContentScale.Crop,
        modifier = modifier,
    )
}
