package dev.tomasflopes.musicplayer.ui.playlists

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.tomasflopes.musicplayer.core.model.playlists.Playlist
import dev.tomasflopes.musicplayer.ui.components.ActionHeight
import dev.tomasflopes.musicplayer.ui.components.ActionRow
import dev.tomasflopes.musicplayer.ui.home.sections.songCountLabel
import dev.tomasflopes.musicplayer.ui.theme.Spacing
import kotlinx.coroutines.launch

private val MaxSheetListHeight = 420.dp

/**
 * The fast add (design 7.12): playlists with the most recently used first, "New playlist" on top, one tap adds
 * [target] and closes the sheet with a one-line message. [onAdded] runs after something was chosen, so a screen can
 * end its selection then and not when the sheet is only swiped away.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToPlaylistSheet(
    target: AddTarget,
    onDismiss: () -> Unit,
    onAdded: () -> Unit = {},
    viewModel: AddToPlaylistViewModel = hiltViewModel(),
) {
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var naming by remember { mutableStateOf(false) }

    // Say what happened while the sheet is still there, then slide it away.
    fun finish(message: String?) {
        if (message != null) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            onAdded()
        }
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.padding(bottom = Spacing.s)) {
            ActionRow(Icons.Default.Add, "New playlist") { naming = true }
            LazyColumn(Modifier.heightIn(max = MaxSheetListHeight)) {
                items(playlists, key = { it.id.value }) { playlist ->
                    PlaylistChoice(playlist) { scope.launch { finish(viewModel.add(target, playlist)) } }
                }
            }
        }
    }
    if (naming) {
        PlaylistNameDialog(
            title = "New playlist",
            confirmLabel = "Create",
            onConfirm = { name ->
                naming = false
                scope.launch { finish(viewModel.createAndAdd(target, name)) }
            },
            onDismiss = { naming = false },
        )
    }
}

@Composable
private fun PlaylistChoice(
    playlist: Playlist,
    onClick: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = ActionHeight)
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.l, vertical = Spacing.xxs),
    ) {
        Text(playlist.name, style = MaterialTheme.typography.titleMedium)
        Text(
            songCountLabel(playlist.songCount),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
