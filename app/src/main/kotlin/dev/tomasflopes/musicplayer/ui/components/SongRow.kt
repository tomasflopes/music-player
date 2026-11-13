package dev.tomasflopes.musicplayer.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import dev.tomasflopes.musicplayer.core.model.Song
import dev.tomasflopes.musicplayer.ui.theme.ArtSize
import dev.tomasflopes.musicplayer.ui.theme.Spacing

/** One song in any list: cover, title as tagged, "artist · album". Stateless: the caller decides what a tap does. */
@Composable
fun SongRow(
    song: Song,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = Spacing.s, vertical = Spacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AlbumArt(song, ArtSize.row)
        Column(Modifier.weight(1f).padding(start = Spacing.s)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = song.subtitle(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun Song.subtitle(): String =
    listOfNotNull(artistName, albumTitle?.takeIf { it.isNotBlank() }).joinToString(" · ")
