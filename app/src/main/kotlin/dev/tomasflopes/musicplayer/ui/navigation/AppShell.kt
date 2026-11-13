package dev.tomasflopes.musicplayer.ui.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.tomasflopes.musicplayer.core.model.AlbumId
import dev.tomasflopes.musicplayer.core.model.ArtistId
import dev.tomasflopes.musicplayer.ui.home.HomeScreen
import dev.tomasflopes.musicplayer.ui.home.dynamiclist.DynamicListRoute
import dev.tomasflopes.musicplayer.ui.home.dynamiclist.DynamicListScreen
import dev.tomasflopes.musicplayer.ui.library.LibraryScreen
import dev.tomasflopes.musicplayer.ui.library.album.AlbumRoute
import dev.tomasflopes.musicplayer.ui.library.album.AlbumScreen
import dev.tomasflopes.musicplayer.ui.library.artist.ArtistRoute
import dev.tomasflopes.musicplayer.ui.library.artist.ArtistScreen
import dev.tomasflopes.musicplayer.ui.onboarding.FolderPromptScreen
import dev.tomasflopes.musicplayer.ui.player.MiniPlayerHost
import dev.tomasflopes.musicplayer.ui.search.SearchScreen
import dev.tomasflopes.musicplayer.ui.shell.ShellState
import dev.tomasflopes.musicplayer.ui.shell.ShellViewModel

/** The app below the system bars: the folder prompt until a music folder is chosen, then the tabs. */
@Composable
fun AppShell(
    viewModel: ShellViewModel,
    onPickFolder: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // The Surface gives every state the theme's background and text colour, not only the ones inside a Scaffold.
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when (state) {
            ShellState.LOADING -> Unit
            ShellState.NEEDS_FOLDER -> FolderPromptScreen(onPickFolder)
            ShellState.READY -> Tabs()
        }
    }
}

@Composable
private fun Tabs() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()

    Scaffold(
        bottomBar = { BottomChrome(navController, backStackEntry?.destination) },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = TopLevelDestination.HOME.route,
            modifier = Modifier.padding(padding),
        ) {
            composable(TopLevelDestination.HOME.route) {
                HomeScreen(onNavigate = { route -> navController.navigate(route) })
            }
            composable(
                route = DynamicListRoute.PATTERN,
                arguments = listOf(navArgument(DynamicListRoute.ARG_ID) { type = NavType.StringType }),
            ) { entry ->
                DynamicListScreen(
                    listId = entry.arguments?.getString(DynamicListRoute.ARG_ID).orEmpty(),
                    onBack = { navController.popBackStack() },
                )
            }
            composable(TopLevelDestination.LIBRARY.route) {
                LibraryScreen(
                    onOpenArtist = { navController.navigate(ArtistRoute.create(it)) },
                    onOpenAlbum = { navController.navigate(AlbumRoute.create(it)) },
                )
            }
            composable(TopLevelDestination.SEARCH.route) {
                SearchScreen(
                    onOpenArtist = { navController.navigate(ArtistRoute.create(it)) },
                    onOpenAlbum = { navController.navigate(AlbumRoute.create(it)) },
                )
            }
            composable(
                route = AlbumRoute.PATTERN,
                arguments = listOf(navArgument(AlbumRoute.ARG_ID) { type = NavType.LongType }),
            ) { entry ->
                AlbumScreen(
                    albumId = AlbumId(entry.arguments?.getLong(AlbumRoute.ARG_ID) ?: 0L),
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                route = ArtistRoute.PATTERN,
                arguments = listOf(navArgument(ArtistRoute.ARG_ID) { type = NavType.LongType }),
            ) { entry ->
                ArtistScreen(
                    artistId = ArtistId(entry.arguments?.getLong(ArtistRoute.ARG_ID) ?: 0L),
                    onBack = { navController.popBackStack() },
                    onOpenAlbum = { navController.navigate(AlbumRoute.create(it)) },
                )
            }
        }
    }
}

/** The mini-player sits directly above the tab bar, on every tab. */
@Composable
private fun BottomChrome(
    navController: NavHostController,
    current: NavDestination?,
) {
    Column {
        MiniPlayerHost()
        NavigationBar {
            TopLevelDestination.entries.forEach { destination ->
                NavigationBarItem(
                    selected = current?.hierarchy?.any { it.route == destination.route } == true,
                    onClick = { navController.navigateToTopLevel(destination) },
                    icon = { Icon(destination.icon, contentDescription = null) },
                    label = { Text(destination.label) },
                )
            }
        }
    }
}

/** Switching tabs keeps each tab's state and never stacks copies of the same tab. */
private fun NavHostController.navigateToTopLevel(destination: TopLevelDestination) {
    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
