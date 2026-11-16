package dev.tomasflopes.musicplayer.ui.navigation

/**
 * Pages opened from Home (Settings, a dynamic list, a playlist) sit above the Home entry in the same back stack.
 * Selecting Home while on one of them must pop back to Home instead of restoring the saved stack, which would
 * put the same page back.
 */
object HomeStackRule {
    /**
     * `true` when the screen showing is not Home itself and no other tab is in the back stack: the pages above
     * Home belong to Home, so they can be dropped. With another tab in the stack the normal tab switch applies,
     * which keeps that tab's state.
     */
    fun popsToHome(
        currentRoute: String?,
        otherTabOnStack: Boolean,
    ): Boolean = currentRoute != null && currentRoute != TopLevelDestination.HOME.route && !otherTabOnStack
}
