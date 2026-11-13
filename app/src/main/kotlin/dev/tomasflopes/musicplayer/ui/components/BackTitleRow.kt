package dev.tomasflopes.musicplayer.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.tomasflopes.musicplayer.ui.theme.Spacing

/** The top row of a page opened from a tab: a back button and, when the page has no header of its own, [title]. */
@Composable
fun BackTitleRow(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack, modifier = Modifier.padding(start = Spacing.xxxs)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }
        if (title != null) ScreenTitle(title)
    }
}
