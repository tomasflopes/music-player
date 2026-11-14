package dev.tomasflopes.musicplayer.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import dev.tomasflopes.musicplayer.core.model.Song
import dev.tomasflopes.musicplayer.ui.theme.ArtSize
import dev.tomasflopes.musicplayer.ui.theme.Spacing

private const val SELECTED_ALPHA = 0.18f

/**
 * One song in any list: cover, title as tagged, "artist · album". Stateless: the caller decides what a tap does and
 * what, if anything, goes at the end of the row ([trailing], e.g. a [SongMenuButton]). An album page swaps the cover
 * for the track number ([leading]) and drops the album from the [subtitle]. A long press ([onLongClick]) starts a
 * selection (ADR 0040); a [selected] row is tinted.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongRow(
    song: Song,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    leading: @Composable () -> Unit = { AlbumArt(song, ArtSize.row) },
    subtitle: String = song.subtitle(),
    onLongClick: (() -> Unit)? = null,
    selected: Boolean = false,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .background(
                    if (selected) MaterialTheme.colorScheme.primary.copy(alpha = SELECTED_ALPHA) else Color.Transparent,
                ).combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .padding(horizontal = Spacing.s, vertical = Spacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading()
        Column(Modifier.weight(1f).padding(start = Spacing.s)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        trailing?.invoke()
    }
}

private fun Song.subtitle(): String =
    listOfNotNull(artistName, albumTitle?.takeIf { it.isNotBlank() }).joinToString(" · ")
