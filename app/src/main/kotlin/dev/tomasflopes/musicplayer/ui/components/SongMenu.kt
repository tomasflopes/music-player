package dev.tomasflopes.musicplayer.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import dev.tomasflopes.musicplayer.core.model.SongId
import dev.tomasflopes.musicplayer.ui.playlists.AddTarget
import dev.tomasflopes.musicplayer.ui.playlists.AddToPlaylistSheet
import dev.tomasflopes.musicplayer.ui.theme.Spacing
import kotlinx.coroutines.launch

internal val ActionHeight = 64.dp

/**
 * The "more" button of a song row. It opens a bottom sheet with Play next, Add to queue and Add to playlist as large
 * rows, easy to hit with a thumb. The caller binds the queue actions; adding to a playlist is the same sheet
 * everywhere, so the button opens it for [songId] itself.
 */
@Composable
fun SongMenuButton(
    songId: SongId,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    var addingToPlaylist by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }, modifier = modifier) {
        Icon(Icons.Default.MoreVert, contentDescription = "Song actions")
    }
    if (open) {
        SongActionsSheet(
            onPlayNext = onPlayNext,
            onAddToQueue = onAddToQueue,
            onAddToPlaylist = { addingToPlaylist = true },
            onDismiss = { open = false },
        )
    }
    if (addingToPlaylist) {
        AddToPlaylistSheet(AddTarget.Songs(listOf(songId)), onDismiss = { addingToPlaylist = false })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SongActionsSheet(
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    // Run the action, then slide the sheet away before it leaves the composition.
    fun choose(action: () -> Unit) {
        action()
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.padding(bottom = Spacing.s)) {
            ActionRow(Icons.Default.SkipNext, "Play next") { choose(onPlayNext) }
            ActionRow(Icons.Default.Add, "Add to queue") { choose(onAddToQueue) }
            ActionRow(Icons.AutoMirrored.Filled.PlaylistAdd, "Add to playlist") { choose(onAddToPlaylist) }
        }
    }
}

@Composable
internal fun ActionRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = ActionHeight)
                .clickable(onClick = onClick)
                .padding(horizontal = Spacing.l),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(text = label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = Spacing.s))
    }
}
