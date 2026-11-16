package dev.tomasflopes.musicplayer.ui.search

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HideKeyboardOnScrollTest {
    private var hidden = 0
    private val connection = HideKeyboardOnScroll { hidden++ }

    private fun drag(consumedY: Float) =
        connection.onPostScroll(Offset(0f, consumedY), Offset.Zero, NestedScrollSource.UserInput)

    @Test
    fun `the first scroll the list consumes hides the keyboard`() {
        drag(-12f)
        assertEquals(1, hidden)
    }

    @Test
    fun `the rest of the same gesture does not hide it again`() {
        drag(-12f)
        drag(-30f)
        drag(8f)
        assertEquals(1, hidden)
    }

    @Test
    fun `a drag the list does not consume leaves the keyboard alone`() {
        drag(0f)
        assertEquals(0, hidden)
    }

    @Test
    fun `a programmatic scroll leaves the keyboard alone`() {
        connection.onPostScroll(Offset(0f, -40f), Offset.Zero, NestedScrollSource.SideEffect)
        assertEquals(0, hidden)
    }

    @Test
    fun `the next gesture hides it again once the previous one ended`() =
        runTest {
            drag(-12f)
            connection.onPostFling(Velocity.Zero, Velocity.Zero)
            drag(-12f)
            assertEquals(2, hidden)
        }
}
