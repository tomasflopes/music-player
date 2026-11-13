package dev.tomasflopes.musicplayer.core.taste.lists

import app.cash.turbine.test
import dev.tomasflopes.musicplayer.core.model.config.LibraryConfig
import dev.tomasflopes.musicplayer.core.model.lists.DynamicListProvider
import dev.tomasflopes.musicplayer.core.model.log.PlaySource
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class RecentlyAddedProviderTest : DynamicListProviderContract() {
    private val now = Instant.parse("2026-11-13T12:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val nowSec = now.epochSecond
    private val day = 86_400L
    private val reader =
        FakeSongReader(listOf(song(1, nowSec - 30 * day), song(2, nowSec - 18 * day), song(3, nowSec - day)))
    private val settings = FakeSettings(days = 20)

    override fun provider(): DynamicListProvider = RecentlyAddedProvider(reader, settings, clock)

    @Test
    fun `window is days times 86400 seconds back from now`() =
        runTest {
            provider().songs().test {
                assertEquals(listOf(3L, 2L), awaitItem().map { it.id.value })
                cancelAndIgnoreRemainingEvents()
            }

            assertEquals(listOf(nowSec - 1_728_000), reader.addedSinceCalls)
        }

    @Test
    fun `changing days from 20 to 15 re-emits`() =
        runTest {
            provider().songs().test {
                assertEquals(listOf(3L, 2L), awaitItem().map { it.id.value })

                settings.setRecentlyAddedDays(15)

                assertEquals(listOf(3L), awaitItem().map { it.id.value })
                cancelAndIgnoreRemainingEvents()
            }

            assertEquals(listOf(nowSec - 1_728_000, nowSec - 1_296_000), reader.addedSinceCalls)
        }

    @Test
    fun `default comes from LibraryConfig and is 20`() {
        assertEquals(20, LibraryConfig().recentlyAddedDefaultDays)
    }

    @Test
    fun `identity on Home`() {
        val provider = provider()

        assertEquals("recently_added", provider.id)
        assertEquals("Recently added", provider.title)
        assertEquals(0, provider.order)
        assertEquals(PlaySource.RECENTLY_ADDED, provider.playSource)
    }
}
