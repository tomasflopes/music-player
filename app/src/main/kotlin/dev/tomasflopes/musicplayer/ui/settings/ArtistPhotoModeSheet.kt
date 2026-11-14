package dev.tomasflopes.musicplayer.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.tomasflopes.musicplayer.core.artistimage.ArtistPhotoMode
import dev.tomasflopes.musicplayer.ui.theme.Spacing

internal fun artistPhotoLabel(mode: ArtistPhotoMode): String =
    when (mode) {
        ArtistPhotoMode.OFF -> "Off"
        ArtistPhotoMode.WIFI_ONLY -> "Wi-Fi only"
        ArtistPhotoMode.ALWAYS -> "Always"
    }

/** Off, Wi-Fi only or Always. Only the artist's name is sent, and never when this is Off (ADR 0028). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistPhotoModeSheet(
    selected: ArtistPhotoMode,
    onSelect: (ArtistPhotoMode) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = Spacing.s)) {
            Text(
                text = "Artist photos from the internet",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = Spacing.s, vertical = Spacing.xs),
            )
            Text(
                text = "Looks up each artist's photo by name on Deezer. Nothing else is sent.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.s),
            )
            ArtistPhotoMode.entries.forEach { mode ->
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelect(mode)
                                onDismiss()
                            }.padding(horizontal = Spacing.s, vertical = Spacing.xxs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = mode == selected, onClick = null)
                    Text(artistPhotoLabel(mode), Modifier.padding(start = Spacing.s))
                }
            }
        }
    }
}
