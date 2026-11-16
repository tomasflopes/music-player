package dev.tomasflopes.musicplayer.ui.mixes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import dev.tomasflopes.musicplayer.ui.components.AlbumArt
import dev.tomasflopes.musicplayer.ui.components.CardCaption
import dev.tomasflopes.musicplayer.ui.theme.ArtSize

internal fun seedCountLabel(count: Int): String = if (count == 1) "1 seed" else "$count seeds"

/** The first card of the mixes row on Home: same size as a [MixCard], with a plus. */
@Composable
fun NewMixCard(
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
        CardCaption("New mix", subtitle = null)
    }
}

/** One mix on Home: tap the cover to play it, "Edit" to change it. */
@Composable
fun MixCard(
    item: MixItem,
    onPlay: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.width(ArtSize.card)) {
        Column(Modifier.clip(MaterialTheme.shapes.medium).clickable(onClick = onPlay)) {
            AlbumArt(item.cover, ArtSize.card)
            CardCaption(item.mix.name, seedCountLabel(item.mix.seeds.size))
        }
        TextButton(onClick = onEdit) { Text("Edit") }
    }
}
