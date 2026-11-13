package dev.tomasflopes.musicplayer.core.taste.lists

import dev.tomasflopes.musicplayer.core.model.lists.DynamicListProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Every [DynamicListProvider] must pass these. A new list's test class extends this one. */
abstract class DynamicListProviderContract {
    /** A provider over a library that has at least one song that belongs in the list. */
    abstract fun provider(): DynamicListProvider

    @Test
    fun `id is non-blank and stable`() {
        assertTrue(provider().id.isNotBlank())
        assertEquals(provider().id, provider().id)
    }

    @Test
    fun `title is non-blank`() {
        assertTrue(provider().title.isNotBlank())
    }

    @Test
    fun `songs emits at least once and the first list is not empty`() =
        runTest {
            assertTrue(provider().songs().first().isNotEmpty())
        }

    @Test
    fun `no song appears twice`() =
        runTest {
            val songs = provider().songs().first()

            assertEquals(songs.size, songs.map { it.id }.toSet().size)
        }
}
