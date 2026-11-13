package dev.tomasflopes.musicplayer.core.taste

import dev.tomasflopes.musicplayer.architecture.OnionRules
import org.junit.jupiter.api.Test

/** Pure Kotlin that depends on :core:model only (rules in [OnionRules]). */
class ArchitectureTest {
    @Test
    fun `core taste respects the onion rules`() = OnionRules.checkCore("taste")
}
