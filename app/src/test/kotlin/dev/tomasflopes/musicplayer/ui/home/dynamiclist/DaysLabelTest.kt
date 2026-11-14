package dev.tomasflopes.musicplayer.ui.home.dynamiclist

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DaysLabelTest {
    @Test
    fun `the window is named in words`() = assertEquals("Last 20 days", daysLabel(20))

    @Test
    fun `one day is singular`() = assertEquals("Last day", daysLabel(1))
}
