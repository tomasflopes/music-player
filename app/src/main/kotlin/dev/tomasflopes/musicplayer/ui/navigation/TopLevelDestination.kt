package dev.tomasflopes.musicplayer.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * The bottom tabs, in order (Stats holds Wrapped, ADR 0091). Routes carry no
 * arguments yet; the first detail route (album, artist) brings typed routes with it.
 */
enum class TopLevelDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    HOME("home", "Home", Icons.Default.Home),
    LIBRARY("library", "Library", Icons.Default.LibraryMusic),
    SEARCH("search", "Search", Icons.Default.Search),
    STATS("stats", "Stats", Icons.Default.BarChart),
}
