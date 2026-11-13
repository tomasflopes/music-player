package dev.tomasflopes.musicplayer.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * The bottom tabs, in order. Search joins with its screen (ADR 0012), Stats with Wrapped. Routes carry no
 * arguments yet; the first detail route (album, artist) brings typed routes with it.
 */
enum class TopLevelDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    HOME("home", "Home", Icons.Default.Home),
    LIBRARY("library", "Library", Icons.Default.LibraryMusic),
}
