package dev.tomasflopes.musicplayer.ui.navigation

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HomeStackRuleTest {
    @Test
    fun `a page opened from Home pops to Home`() {
        assertTrue(HomeStackRule.popsToHome("settings", otherTabOnStack = false))
        assertTrue(HomeStackRule.popsToHome("playlist/3", otherTabOnStack = false))
    }

    @Test
    fun `Home itself has nothing to pop`() {
        assertFalse(HomeStackRule.popsToHome("home", otherTabOnStack = false))
    }

    @Test
    fun `another tab in the stack keeps the normal tab switch and its saved state`() {
        assertFalse(HomeStackRule.popsToHome("library", otherTabOnStack = true))
        assertFalse(HomeStackRule.popsToHome("album/1", otherTabOnStack = true))
    }

    @Test
    fun `no current screen does not match`() {
        assertFalse(HomeStackRule.popsToHome(null, otherTabOnStack = false))
    }
}
