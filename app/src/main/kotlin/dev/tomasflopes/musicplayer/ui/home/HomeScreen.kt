package dev.tomasflopes.musicplayer.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.tomasflopes.musicplayer.core.model.ports.ScanProgress
import dev.tomasflopes.musicplayer.ui.components.ScreenTitle
import dev.tomasflopes.musicplayer.ui.theme.Spacing

@Composable
fun HomeScreen(viewModel: HomeViewModel = hiltViewModel()) {
    val scan by viewModel.scan.collectAsStateWithLifecycle()
    HomeContent(scan, viewModel::shuffleAll, viewModel::rescan)
}

/** Shuffle all and the library's scan state. Recently added and the other sections come with Home's own task. */
@Composable
private fun HomeContent(
    scan: ScanProgress,
    onShuffleAll: () -> Unit,
    onRescan: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        ScreenTitle("Home")
        Button(
            onClick = onShuffleAll,
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.s),
        ) {
            Icon(Icons.Default.Shuffle, contentDescription = null)
            Spacer(Modifier.width(Spacing.xxs))
            Text("Shuffle all")
        }
        ScanStatus(scan, onRescan, Modifier.padding(horizontal = Spacing.s, vertical = Spacing.s))
    }
}
