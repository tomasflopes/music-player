package dev.tomasflopes.musicplayer.ui.home

import dev.tomasflopes.musicplayer.core.model.log.PlaySource
import dev.tomasflopes.musicplayer.fakes.FakeDynamicListProvider
import dev.tomasflopes.musicplayer.fakes.testSong
import dev.tomasflopes.musicplayer.ui.home.sections.DynamicListCard
import dev.tomasflopes.musicplayer.ui.home.sections.DynamicListsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DynamicListsViewModelTest {
    private val recentlyAdded =
        FakeDynamicListProvider(
            id = "recently_added",
            order = 0,
            title = "Recently added",
            playSource = PlaySource.RECENTLY_ADDED,
            songs = listOf(testSong(1), testSong(2)),
        )

    @BeforeEach
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    /** Keeps the screen's flow subscribed, as the screen does, so `value` follows the providers. */
    private fun TestScope.subscribed(cards: StateFlow<List<DynamicListCard>>): StateFlow<List<DynamicListCard>> {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { cards.collect {} }
        return cards
    }

    @Test
    fun `one card per provider with its title and song count`() =
        runTest {
            val cards = subscribed(DynamicListsViewModel(setOf(recentlyAdded)).cards)

            assertEquals(listOf(DynamicListCard("recently_added", "Recently added", 2, testSong(1))), cards.value)
        }

    @Test
    fun `a second provider gets its own card, in order, with no change to Home`() =
        runTest {
            val discover = FakeDynamicListProvider(id = "discover", order = 5, title = "Discover")
            val rewind =
                FakeDynamicListProvider(id = "rewind", order = 1, title = "Rewind", songs = listOf(testSong(9)))

            val cards = subscribed(DynamicListsViewModel(setOf(discover, rewind, recentlyAdded)).cards)

            assertEquals(listOf("recently_added", "rewind", "discover"), cards.value.map { it.id })
        }

    @Test
    fun `an empty list still has a card, without a cover`() =
        runTest {
            val cards = subscribed(DynamicListsViewModel(setOf(FakeDynamicListProvider(id = "empty"))).cards)

            assertEquals(0, cards.value.single().songCount)
            assertNull(cards.value.single().cover)
        }

    @Test
    fun `a card follows its list when the list changes`() =
        runTest {
            val cards = subscribed(DynamicListsViewModel(setOf(recentlyAdded)).cards)

            recentlyAdded.list.value = listOf(testSong(1), testSong(2), testSong(3))

            assertEquals(3, cards.value.single().songCount)
        }

    @Test
    fun `no providers means no cards`() =
        runTest {
            val cards = subscribed(DynamicListsViewModel(emptySet()).cards)

            assertEquals(emptyList<DynamicListCard>(), cards.value)
        }
}
