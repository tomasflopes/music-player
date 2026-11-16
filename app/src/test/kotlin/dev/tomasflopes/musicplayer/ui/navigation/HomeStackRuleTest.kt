package dev.tomasflopes.musicplayer.ui.navigation

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HomeStackRuleTest {
    @Test
    fun `a page opened from Home above Home pops to Home`() {
        assertTrue(HomeStackRule.popsToHome(listOf(null, "home", "settings")))
        assertTrue(HomeStackRule.popsToHome(listOf(null, "home", "dynamic/recent", "playlist/3")))
    }

    @Test
    fun `Home alone has nothing to pop`() {
        assertFalse(HomeStackRule.popsToHome(listOf(null, "home")))
    }

    @Test
    fun `another tab in the stack keeps the normal tab switch and its saved state`() {
        assertFalse(HomeStackRule.popsToHome(listOf(null, "home", "library")))
        assertFalse(HomeStackRule.popsToHome(listOf(null, "home", "library", "album/1")))
        assertFalse(HomeStackRule.popsToHome(listOf(null, "home", "search")))
    }

    @Test
    fun `a stack without Home does not match`() {
        assertFalse(HomeStackRule.popsToHome(emptyList()))
        assertFalse(HomeStackRule.popsToHome(listOf(null, "settings")))
    }
}
