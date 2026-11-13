package dev.tomasflopes.musicplayer.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.tomasflopes.musicplayer.ui.theme.Spacing

/** The large title at the top of a tab. */
@Composable
fun ScreenTitle(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineMedium,
        modifier = modifier.padding(horizontal = Spacing.s, vertical = Spacing.s),
    )
}
