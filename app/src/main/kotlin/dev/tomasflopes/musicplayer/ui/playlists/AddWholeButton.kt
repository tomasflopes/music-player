package dev.tomasflopes.musicplayer.ui.playlists

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.tomasflopes.musicplayer.ui.theme.Spacing

/** "Add whole album" / "Add whole artist": one button that opens the add sheet for the whole [target]. */
@Composable
fun AddWholeButton(
    label: String,
    target: AddTarget,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var open by remember { mutableStateOf(false) }
    OutlinedButton(
        onClick = { open = true },
        enabled = enabled,
        modifier = modifier.fillMaxWidth().padding(horizontal = Spacing.s),
    ) {
        Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null)
        Text(label, modifier = Modifier.padding(start = Spacing.xxs))
    }
    if (open) AddToPlaylistSheet(target, onDismiss = { open = false })
}
