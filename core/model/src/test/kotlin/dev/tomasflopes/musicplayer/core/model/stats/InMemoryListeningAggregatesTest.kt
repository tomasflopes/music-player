package dev.tomasflopes.musicplayer.core.model.stats

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The in-memory reference implementation meets the contract the Room one must meet too. */
class InMemoryListeningAggregatesTest {
    @Test
    fun `the in-memory aggregates meet the contract`() {
        val failures =
            ListeningAggregatesContract
                .checks { songs, plays -> InMemoryListeningAggregates(songs, plays) }
                .mapNotNull { check ->
                    runCatching { check.run() }.exceptionOrNull()?.let { "${check.name}: ${it.message}" }
                }

        assertTrue(failures.isEmpty(), "Failed checks:\n${failures.joinToString("\n")}")
    }
}
