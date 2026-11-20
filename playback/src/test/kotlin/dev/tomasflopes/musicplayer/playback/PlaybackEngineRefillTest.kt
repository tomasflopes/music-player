package dev.tomasflopes.musicplayer.playback

import androidx.media3.common.Player
import dev.tomasflopes.musicplayer.core.model.log.PlaySource
import dev.tomasflopes.musicplayer.core.model.queue.RefillSeed
import dev.tomasflopes.musicplayer.playback.queue.RepeatMode
import dev.tomasflopes.musicplayer.playback.queue.names
import dev.tomasflopes.musicplayer.playback.queue.refs
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class PlaybackEngineRefillTest {
    private val seed = RefillSeed.Song(9)

    /** A queue of [names] extended from song 9, as a solo play leaves it. */
    private fun seeded(names: String): EngineHarness =
        EngineHarness(names).also {
            it.queue.replace(refs(names), 0, PlaySource.SEARCH, false, soloSeed = 9L)
        }

    private fun EngineHarness.append(
        names: String,
        epoch: Int = queue.epoch,
        forSeed: RefillSeed = seed,
    ) = engine.appendBatch(refs(names), forSeed, epoch, PlaySource.RADIO)

    @Test
    fun `a batch is appended after the last auto entry`() {
        val harness = seeded("AB")
        harness.startPlaying()

        harness.append("XY")

        assertEquals("ABXY", harness.queue.names())
    }

    @Test
    fun `a batch built for an earlier epoch appends nothing`() {
        val harness = seeded("AB")
        val stale = harness.queue.epoch - 1

        harness.append("XY", epoch = stale)

        assertEquals("AB", harness.queue.names())
    }

    @Test
    fun `a batch for another seed appends nothing`() {
        val harness = seeded("AB")

        harness.append("XY", forSeed = RefillSeed.Song(5))

        assertEquals("AB", harness.queue.names())
    }

    @Test
    fun `a batch is dropped while repeat is on`() {
        val harness = seeded("AB")
        harness.queue.repeatMode = RepeatMode.ALL

        harness.append("XY")

        assertEquals("AB", harness.queue.names())
    }

    @Test
    fun `songs already queued, played ones included, are left out`() {
        val harness = seeded("ABC")
        harness.queue.jumpTo(2)

        harness.append("AXBY")

        assertEquals("ABCXY", harness.queue.names())
    }

    @Test
    fun `a batch of only queued songs changes nothing`() {
        val harness = seeded("AB")

        harness.append("BA")

        assertEquals("AB", harness.queue.names())
    }

    @Test
    fun `an append announces itself so the state republishes`() {
        val harness = seeded("AB")
        var announced = 0
        harness.engine.exposed.addListener(
            object : Player.Listener {
                override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                    announced++
                }
            },
        )

        harness.append("XY")

        assertEquals(1, announced)
    }

    @Test
    fun `playing with a solo seed marks every entry of the queue`() {
        val harness = EngineHarness("A")

        harness.engine.play(PlayRequest(refs("A"), 0, shuffle = false, PlaySource.SEARCH, picked = true, soloSeed = 9L))

        assertEquals(seed, harness.queue.refillSignal()?.seed)
        assertEquals(listOf<Long?>(9L), harness.queue.snapshot().entries.map { it.soloSeed })
    }

    @Test
    fun `a plain play leaves the queue without a seed`() {
        val harness = EngineHarness("A")

        harness.engine.play(PlayRequest(refs("AB"), 0, shuffle = false, PlaySource.ALBUM, picked = true))

        assertNull(harness.queue.refillSignal())
    }
}
