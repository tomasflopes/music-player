package dev.tomasflopes.musicplayer.playback

import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.tomasflopes.musicplayer.core.model.ArtistId
import dev.tomasflopes.musicplayer.core.model.SongId
import dev.tomasflopes.musicplayer.core.model.SongRef
import dev.tomasflopes.musicplayer.core.model.log.PlaySource
import dev.tomasflopes.musicplayer.core.model.queue.RefillSeed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The bundles of the refill command and of the solo seed, in `deviceCheck`: a [android.os.Bundle] is not available on
 * the JVM. NOT RUN: written without an emulator (ADR 0078); run `./gradlew deviceCheck` before trusting it.
 */
@RunWith(AndroidJUnit4::class)
class PlaybackCommandsRefillTest {
    private fun refs(vararg ids: Long) = ids.map { SongRef(SongId(it), ArtistId(1)) }

    @Test
    fun a_refill_batch_round_trips_through_a_bundle() {
        val songs = refs(1, 2, 3)

        listOf(RefillSeed.Song(9), RefillSeed.Mix(4)).forEach { seed ->
            val bundle = PlaybackCommands.bundleOf(songs, seed, 7, PlaySource.MIX)

            assertEquals(songs, PlaybackCommands.refsOf(bundle))
            assertEquals(seed, PlaybackCommands.seedOf(bundle))
            assertEquals(7, PlaybackCommands.epochOf(bundle))
            assertEquals(PlaySource.MIX, PlaybackCommands.sourceOf(bundle))
        }
    }

    @Test
    fun a_play_request_keeps_its_solo_seed_and_a_plain_one_has_none() {
        val seeded = PlayRequest(refs(1), 0, false, PlaySource.SEARCH, picked = true, soloSeed = 9L)
        val plain = PlayRequest(refs(1), 0, false, PlaySource.ALBUM, picked = true)

        assertEquals(9L, PlaybackCommands.requestOf(PlaybackCommands.bundleOf(seeded)).soloSeed)
        assertNull(PlaybackCommands.requestOf(PlaybackCommands.bundleOf(plain)).soloSeed)
    }

    @Test
    fun a_removed_entry_keeps_its_solo_seed_for_the_undo() {
        val removed =
            RemovedQueueEntry(
                index = 3,
                ref = refs(1).single(),
                source = PlaySource.RADIO,
                manual = false,
                playlistId = null,
                wasCurrent = false,
                positionMs = 0,
                wasPlaying = false,
                soloSeed = 9L,
            )

        assertEquals(removed, PlaybackCommands.removedOf(PlaybackCommands.bundleOf(removed)))
    }
}
