package dev.tomasflopes.musicplayer.playback

import dev.tomasflopes.musicplayer.architecture.OnionRules
import org.junit.jupiter.api.Test

/** Depends inward on :core:* only, never on a sibling layer (rules in [OnionRules]). */
class ArchitectureTest {
    @Test
    fun `playback respects the onion rules`() = OnionRules.checkOuter("playback")
}
