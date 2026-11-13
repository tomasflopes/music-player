package dev.tomasflopes.musicplayer.data

import dev.tomasflopes.musicplayer.architecture.OnionRules
import org.junit.jupiter.api.Test

/** Depends inward on :core:* only, never on a sibling layer (rules in [OnionRules]). */
class ArchitectureTest {
    @Test
    fun `data respects the onion rules`() = OnionRules.checkOuter("data")
}
