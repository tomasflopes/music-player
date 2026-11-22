package dev.tomasflopes.musicplayer.ui.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import dev.tomasflopes.musicplayer.ui.stats.StatsScreen
import dev.tomasflopes.musicplayer.ui.stats.WrappedRoute
import dev.tomasflopes.musicplayer.ui.stats.WrappedScreen

/** The Stats tab and the Wrapped page of a past month or year, opened from its lists. */
internal fun NavGraphBuilder.statsDestinations(navController: NavHostController) {
    composable(TopLevelDestination.STATS.route) {
        StatsScreen(onOpenPeriod = { navController.navigate(WrappedRoute.create(it)) })
    }
    composable(
        route = WrappedRoute.PATTERN,
        arguments = listOf(navArgument(WrappedRoute.ARG_KEY) { type = NavType.StringType }),
    ) {
        WrappedScreen(onBack = { navController.popBackStack() })
    }
}
