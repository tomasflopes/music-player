package dev.tomasflopes.musicplayer.tools.analyser

import dev.tomasflopes.musicplayer.architecture.OnionRules
import org.junit.jupiter.api.Test

/** A tool depends on `:core:*` only (rules in [OnionRules], ADR 0013 and 0053). */
class ArchitectureTest {
    @Test
    fun `the analyser depends on core only`() = OnionRules.checkTool("analyser")
}
