package dev.tomasflopes.musicplayer.ui.playlists

import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import dev.tomasflopes.musicplayer.ui.theme.Spacing
import kotlinx.coroutines.launch

/**
 * "Save as playlist": asks for a name (starting at [defaultName]), then runs [save] and shows its message
 * (ADR 0044). [save] is `viewModel.saveList(...)` for a list or `viewModel.saveQueue(...)` for the queue.
 */
@Composable
fun SaveAsPlaylistButton(
    defaultName: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    viewModel: SaveAsPlaylistViewModel = hiltViewModel(),
    save: suspend SaveAsPlaylistViewModel.(name: String) -> String,
) {
    var naming by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    OutlinedButton(
        onClick = { naming = true },
        enabled = enabled,
        modifier = modifier.fillMaxWidth().padding(horizontal = Spacing.s),
    ) {
        Icon(Icons.Filled.LibraryAdd, contentDescription = null)
        Text("Save as playlist", modifier = Modifier.padding(start = Spacing.xxs))
    }
    if (naming) {
        PlaylistNameDialog(
            title = "Save as playlist",
            confirmLabel = "Save",
            initial = defaultName,
            onConfirm = { name ->
                naming = false
                scope.launch { Toast.makeText(context, viewModel.save(name), Toast.LENGTH_SHORT).show() }
            },
            onDismiss = { naming = false },
        )
    }
}
