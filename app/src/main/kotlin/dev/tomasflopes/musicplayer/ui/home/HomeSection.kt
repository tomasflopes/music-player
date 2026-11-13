package dev.tomasflopes.musicplayer.ui.home

import androidx.compose.runtime.Composable

/**
 * One block of the Home tab. Extension point (design 10): a new block is a new implementation registered with a
 * Hilt `@IntoSet` binding in `ExtensionPointsModule`; Home itself never changes.
 */
interface HomeSection {
    /** Unique among sections; keys the item in the list. */
    val id: String

    /** Position on Home; lower comes first, ties are broken by [id]. */
    val order: Int

    /** [onNavigate] takes a route string, the same plain strings the tabs use. */
    @Composable
    fun Content(onNavigate: (String) -> Unit)
}
