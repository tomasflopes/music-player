package dev.tomasflopes.musicplayer.ui.library.artist.photo

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.tomasflopes.musicplayer.core.model.Artist
import dev.tomasflopes.musicplayer.ui.components.ArtistAvatar
import dev.tomasflopes.musicplayer.ui.theme.ArtSize
import dev.tomasflopes.musicplayer.ui.theme.Spacing

/** The top of the artist page: a large picture and the way to change it. */
@Composable
fun ArtistPhotoHeader(
    artist: Artist,
    onChangePhoto: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = Spacing.s),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ArtistAvatar(artist.id, artist.name, ArtSize.header)
        TextButton(onClick = onChangePhoto) { Text("Change photo") }
    }
}
