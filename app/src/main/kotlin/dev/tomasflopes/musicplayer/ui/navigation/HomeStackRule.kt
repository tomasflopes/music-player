package dev.tomasflopes.musicplayer.ui.navigation

/**
 * Pages opened from Home (Settings, a dynamic list, a playlist) sit above the Home entry in the same back stack.
 * Selecting Home while on one of them must pop back to Home instead of restoring the saved stack, which would
 * put the same page back.
 */
object HomeStackRule {
    /**
     * `true` when [stackRoutes] (bottom to top, `null` for entries without a route) holds something above Home
     * and no other tab: the pages on top belong to Home, so they can be dropped. With another tab in the stack
     * the normal tab switch applies, which keeps that tab's state.
     */
    fun popsToHome(stackRoutes: List<String?>): Boolean {
        val tabs = stackRoutes.filter { route -> TopLevelDestination.entries.any { it.route == route } }
        val homeIndex = stackRoutes.indexOf(TopLevelDestination.HOME.route)
        return tabs == listOf(TopLevelDestination.HOME.route) && homeIndex in 0 until stackRoutes.lastIndex
    }
}
