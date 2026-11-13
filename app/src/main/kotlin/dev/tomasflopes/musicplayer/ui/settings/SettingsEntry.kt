package dev.tomasflopes.musicplayer.ui.settings

import androidx.compose.runtime.Composable

/**
 * One extra row of the Settings screen, contributed with a Hilt `@IntoSet` binding (ADR 0019). Settings draws the
 * set in [order] and never names a member; the debug source set adds the link to its screen this way.
 */
interface SettingsEntry {
    /** Unique among entries; keys the row. */
    val id: String

    /** Position below the built-in rows; lower comes first, ties are broken by [id]. */
    val order: Int

    @Composable
    fun Content()
}
