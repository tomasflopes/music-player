package dev.tomasflopes.musicplayer.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import dev.tomasflopes.musicplayer.core.model.Song
import dev.tomasflopes.musicplayer.ui.theme.ArtSize
import dev.tomasflopes.musicplayer.ui.theme.Spacing

private const val SUBTITLE_LINES = 2

/**
 * A card for a whole list on Home: cover on top, name and a short subtitle under it. Stateless. The Lists cards (Top
 * 100, Recently added) and the Playlists cards are all this one component (ADR 0052).
 */
@Composable
fun ListCard(
    title: String,
    subtitle: String,
    cover: Song?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .width(ArtSize.card)
                .clip(MaterialTheme.shapes.medium)
                .clickable(onClick = onClick),
    ) {
        AlbumArt(cover, ArtSize.card)
        CardCaption(title, subtitle)
    }
}

/**
 * The text under a Home card's cover. The card's rounded press area (`shapes.medium`, a 12 dp corner) used to cut the
 * bottom-left of the last line, "N songs". The caption now ends [Spacing.xs] (12 dp, the corner radius) above the
 * bottom edge, where the rounded corner no longer reaches, and the subtitle wraps to a second line instead of being
 * cut when the font is large.
 */
@Composable
internal fun CardCaption(
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
) {
    Column(modifier.padding(bottom = Spacing.xs)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = Spacing.xxs),
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = SUBTITLE_LINES,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
