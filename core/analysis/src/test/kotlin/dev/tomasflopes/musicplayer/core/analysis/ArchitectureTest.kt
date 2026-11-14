package dev.tomasflopes.musicplayer.core.analysis

import dev.tomasflopes.musicplayer.architecture.OnionRules
import org.junit.jupiter.api.Test

/** Pure Kotlin with no Android types and no sibling core module (rules in [OnionRules]). */
class ArchitectureTest {
    @Test
    fun `core analysis respects the onion rules`() = OnionRules.checkCore("analysis")
}
