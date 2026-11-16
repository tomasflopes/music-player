package dev.tomasflopes.musicplayer.ui.mixes

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.tomasflopes.musicplayer.ui.components.AlbumArt
import dev.tomasflopes.musicplayer.ui.components.CardCaption
import dev.tomasflopes.musicplayer.ui.theme.ArtSize
import dev.tomasflopes.musicplayer.ui.theme.Spacing

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

/** One mix on Home: tap to play it, long-press or the pencil on the cover to edit it. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MixCard(
    item: MixItem,
    onPlay: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .width(ArtSize.card)
            .clip(MaterialTheme.shapes.medium)
            .combinedClickable(onClick = onPlay, onLongClick = onEdit),
    ) {
        Box {
            AlbumArt(item.cover, ArtSize.card)
            EditBadge(onEdit, Modifier.align(Alignment.TopEnd).padding(Spacing.xxs))
        }
        CardCaption(item.mix.name, seedCountLabel(item.mix.seeds.size))
    }
}

/** A small round pencil on the cover, dark enough to read on any artwork. */
@Composable
private fun EditBadge(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .size(EDIT_BADGE_SIZE)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = EDIT_BADGE_SCRIM))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Default.Edit,
            contentDescription = "Edit mix",
            tint = Color.White,
            modifier = Modifier.size(EDIT_ICON_SIZE),
        )
    }
}

private val EDIT_BADGE_SIZE = 32.dp
private val EDIT_ICON_SIZE = 16.dp
private const val EDIT_BADGE_SCRIM = 0.55f
