package dev.tomasflopes.musicplayer.playback

import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.tomasflopes.musicplayer.core.model.SongId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The bundle of the reorder command, in `deviceCheck`: a [android.os.Bundle] is not available on the JVM.
 * NOT RUN: written without an emulator (ADR 0081); run `./gradlew deviceCheck` before trusting it.
 */
@RunWith(AndroidJUnit4::class)
class PlaybackCommandsMoveTest {
    @Test
    fun a_reorder_round_trips_through_a_bundle() {
        val bundle = PlaybackCommands.bundleOf(from = 7, to = 3, songId = SongId(42))

        assertEquals(7, PlaybackCommands.indexOf(bundle))
        assertEquals(3, PlaybackCommands.toOf(bundle))
        assertEquals(SongId(42), PlaybackCommands.songIdOf(bundle))
    }

    @Test
    fun the_reorder_command_is_one_the_service_accepts() {
        assertTrue(PlaybackCommands.moveInQueue in PlaybackCommands.all)
    }
}
