package dev.tomasflopes.musicplayer.ui.playlists

import dev.tomasflopes.musicplayer.core.model.playlists.AddResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AddMessageTest {
    private fun message(
        added: Int,
        alreadyThere: Int,
    ) = AddMessage.of(AddResult(added, alreadyThere), "Gym")

    @Test
    fun `one song added`() = assertEquals("Added to Gym", message(1, 0))

    @Test
    fun `one song that was already there changes nothing and says so`() = assertEquals("Already in Gym", message(0, 1))

    @Test
    fun `several songs added`() = assertEquals("Added 3 songs to Gym", message(3, 0))

    @Test
    fun `a mix of new and old reports both counts`() = assertEquals("Added 2, 5 already there", message(2, 5))

    @Test
    fun `only old songs says they are all there`() = assertEquals("All 4 already in Gym", message(0, 4))

    @Test
    fun `nothing to add says so`() = assertEquals("Nothing to add", message(0, 0))
}
