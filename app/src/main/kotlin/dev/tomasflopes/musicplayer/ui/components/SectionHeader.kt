package dev.tomasflopes.musicplayer.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.tomasflopes.musicplayer.ui.theme.Spacing

/** The title of a group of rows, with a "See all" action when the group has more than it shows. */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    onSeeAll: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(start = Spacing.s, end = Spacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.weight(1f).padding(vertical = Spacing.xxs),
        )
        if (onSeeAll != null) {
            TextButton(onClick = onSeeAll) { Text("See all") }
        }
    }
}
