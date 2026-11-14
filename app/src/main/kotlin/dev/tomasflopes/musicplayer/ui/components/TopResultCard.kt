package dev.tomasflopes.musicplayer.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import dev.tomasflopes.musicplayer.ui.theme.Spacing

/**
 * The best match of a search, large, with Shuffle first and Play in order (ADR 0048), or just Play for one song.
 * [onClick] makes the whole card tappable (open the artist or album page); the buttons keep their own action.
 * [leading] is the artwork slot, so an artist, an album and a song can share the card.
 */
@Composable
fun TopResultCard(
    title: String,
    subtitle: String,
    leading: @Composable () -> Unit,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
    onShuffle: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    menu: (@Composable () -> Unit)? = null,
) {
    val cardModifier = modifier.fillMaxWidth().padding(horizontal = Spacing.s, vertical = Spacing.xxs)
    val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    val body: @Composable () -> Unit = { TopResultBody(title, subtitle, leading, onPlay, onShuffle, menu) }
    if (onClick == null) {
        Card(modifier = cardModifier, colors = colors) { body() }
    } else {
        Card(onClick = onClick, modifier = cardModifier, colors = colors) { body() }
    }
}

@Composable
private fun TopResultBody(
    title: String,
    subtitle: String,
    leading: @Composable () -> Unit,
    onPlay: () -> Unit,
    onShuffle: (() -> Unit)?,
    menu: (@Composable () -> Unit)?,
) {
    Column(Modifier.padding(Spacing.s)) {
        leading()
        Spacer(Modifier.height(Spacing.xs))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(Spacing.s))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onShuffle != null) {
                ShuffleButton(onShuffle)
                Spacer(Modifier.width(Spacing.xxs))
                PlayInOrderButton(onPlay)
            } else {
                // A single song has nothing to shuffle, so Play stays the primary button.
                Button(onClick = onPlay) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(Spacing.xxs))
                    Text("Play")
                }
            }
            if (menu != null) {
                Spacer(Modifier.weight(1f))
                menu()
            }
        }
    }
}
