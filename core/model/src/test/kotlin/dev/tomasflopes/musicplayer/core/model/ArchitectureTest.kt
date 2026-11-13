package dev.tomasflopes.musicplayer.core.model

import dev.tomasflopes.musicplayer.architecture.OnionRules
import org.junit.jupiter.api.Test

/** The centre of the onion: platform-neutral and dependent on no other module (rules in [OnionRules]). */
class ArchitectureTest {
    @Test
    fun `core model respects the onion rules`() = OnionRules.checkCore("model")
}
