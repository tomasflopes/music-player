package dev.tomasflopes.musicplayer.ui.stats

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * One card of a Wrapped period (design §7.10). New cards are new implementations registered with an `@IntoSet`
 * binding in `StatsModule`; the screen reads the set and never names a member (Guidelines §2.4).
 */
interface WrappedCard {
    val id: String

    /** Ascending: the card with the lowest order comes first. */
    val order: Int

    /** False when the period has nothing for this card; the screen then leaves it out. */
    fun hasContent(data: WrappedData): Boolean

    @Composable
    fun Content(
        data: WrappedData,
        modifier: Modifier,
    )
}
