package dev.tomasflopes.musicplayer.ui.home

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.tomasflopes.musicplayer.core.model.ports.ScanProgress
import dev.tomasflopes.musicplayer.ui.components.ScreenTitle
import dev.tomasflopes.musicplayer.ui.settings.SettingsRoute
import dev.tomasflopes.musicplayer.ui.theme.Spacing

/** [onNavigate] opens a route; sections use it to leave Home (a list card opens its screen). */
@Composable
fun HomeScreen(
    onNavigate: (String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val scan by viewModel.scan.collectAsStateWithLifecycle()
    HomeContent(viewModel.sections, scan, onNavigate, viewModel::rescan)
}

/** The registered sections in order, then the library's scan state. */
@Composable
private fun HomeContent(
    sections: List<HomeSection>,
    scan: ScanProgress,
    onNavigate: (String) -> Unit,
    onRescan: () -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize()) {
        item(key = "title") { HomeTitle(onOpenSettings = { onNavigate(SettingsRoute.ROUTE) }) }
        items(sections, key = { it.id }) { section -> section.Content(onNavigate) }
        item(key = "scan") {
            ScanStatus(scan, onRescan, Modifier.padding(horizontal = Spacing.s, vertical = Spacing.s))
        }
    }
}

/** The Home title with the gear that opens Settings, the one way in to it. */
@Composable
private fun HomeTitle(onOpenSettings: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        ScreenTitle("Home", Modifier.weight(1f))
        IconButton(onClick = onOpenSettings, modifier = Modifier.padding(end = Spacing.xxxs)) {
            Icon(Icons.Default.Settings, contentDescription = "Settings")
        }
    }
}
