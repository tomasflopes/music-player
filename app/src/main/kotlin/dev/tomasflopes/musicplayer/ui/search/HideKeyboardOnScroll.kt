package dev.tomasflopes.musicplayer.ui.search

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity

/**
 * Hides the keyboard when the owner's finger starts moving the results. It fires once per gesture, on the first
 * scroll the list actually consumes (a drag on a list that cannot move does nothing, and neither does a
 * programmatic scroll), and arms again when the gesture ends.
 */
class HideKeyboardOnScroll(
    private val hideKeyboard: () -> Unit,
) : NestedScrollConnection {
    private var armed = true

    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource,
    ): Offset {
        if (armed && source == NestedScrollSource.UserInput && consumed.y != 0f) {
            armed = false
            hideKeyboard()
        }
        return Offset.Zero
    }

    override suspend fun onPostFling(
        consumed: Velocity,
        available: Velocity,
    ): Velocity {
        armed = true
        return Velocity.Zero
    }
}
