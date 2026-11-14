package dev.tomasflopes.musicplayer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import dev.tomasflopes.musicplayer.ui.home.sections.songCountLabel
import dev.tomasflopes.musicplayer.ui.playlists.PlaylistItem
import dev.tomasflopes.musicplayer.ui.theme.ArtSize
import dev.tomasflopes.musicplayer.ui.theme.Spacing

/** The first card of the Playlists row on Home: same size as a [ListCard], with a plus. */
@Composable
fun NewPlaylistCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.width(ArtSize.card).clip(MaterialTheme.shapes.medium).clickable(onClick = onClick)) {
        Box(
            Modifier
                .size(ArtSize.card)
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
        Text(
            text = "New playlist",
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = Spacing.xxs),
        )
    }
}

/** One playlist in the Library tab: cover, name, how many songs. */
@Composable
fun PlaylistRow(
    item: PlaylistItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(
                    onClick = onClick,
                ).padding(horizontal = Spacing.s, vertical = Spacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AlbumArt(item.cover, ArtSize.row)
        Column(Modifier.weight(1f).padding(start = Spacing.s)) {
            Text(
                item.playlist.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                songCountLabel(item.playlist.songCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** The first row of the Playlists tab. */
@Composable
fun NewPlaylistRow(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(
                    onClick = onClick,
                ).padding(horizontal = Spacing.s, vertical = Spacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(ArtSize.row), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
        Text(
            "New playlist",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = Spacing.s),
        )
    }
}
