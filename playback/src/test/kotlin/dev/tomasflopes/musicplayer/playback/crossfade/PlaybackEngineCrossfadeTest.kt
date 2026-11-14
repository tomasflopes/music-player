package dev.tomasflopes.musicplayer.playback.crossfade

import dev.tomasflopes.musicplayer.core.model.crossfade.CrossfadeDecision
import dev.tomasflopes.musicplayer.core.model.crossfade.CrossfadeSettings
import dev.tomasflopes.musicplayer.core.model.log.EndedBy
import dev.tomasflopes.musicplayer.core.model.log.PlaySource
import dev.tomasflopes.musicplayer.core.model.log.StartedBy
import dev.tomasflopes.musicplayer.playback.EngineHarness
import dev.tomasflopes.musicplayer.playback.PlayRequest
import dev.tomasflopes.musicplayer.playback.RecordingSaving
import dev.tomasflopes.musicplayer.playback.queue.RepeatMode
import dev.tomasflopes.musicplayer.playback.queue.ref
import dev.tomasflopes.musicplayer.playback.queue.refs
import dev.tomasflopes.musicplayer.playback.song
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The playback engine as the host of a crossfade (ADR 0061, 0062), with a fake crossfade engine, a fake second player
 * and a fake clock: the test calls the host the way the two-player engine would. Nothing here plays audio.
 */
class PlaybackEngineCrossfadeTest {
    private val fake = FakeCrossfadeEngine()
    private val saving = RecordingSaving()
    private val on = CrossfadeSettings(enabled = true, durationMs = 4_000)

    /** The first song of [names] is playing; every song is on its own album, so the policy has no reason to cut. */
    private fun playing(
        names: String = "ABCD",
        settings: CrossfadeSettings = on,
    ): EngineHarness {
        val harness =
            EngineHarness(
                names,
                saving = saving,
                crossfade = fake,
                crossfadeSettings = { settings },
                songOf = { song(it, album = "Album $it") },
            )
        harness.begin(names)
        return harness
    }

    private fun EngineHarness.begin(names: String) {
        engine.play(PlayRequest(refs(names), 0, shuffle = false, PlaySource.ALBUM, picked = true))
        state.playing = true
        state.listener?.onIsPlayingChanged(true)
    }

    /** The two-player engine's part at the start of a fade: the spare holds the next song and is playing. */
    private fun EngineHarness.startFade() {
        secondState.itemCount = 1
        secondState.playing = true
        runBlocking { fake.host.fadeStarted(secondPlayer) }
    }

    /** A is played for [beforeMs], then a fade of [fadeMs] to B. */
    private fun EngineHarness.fade(
        beforeMs: Long = 6_000,
        fadeMs: Long = 4_000,
    ) {
        time += beforeMs
        startFade()
        time += fadeMs
        runBlocking { fake.host.fadeFinished() }
    }

    @Test
    fun `plans a fade between songs of different albums`() {
        val h = playing()

        val plan = runBlocking { fake.host.planNext() }

        assertEquals(CrossfadeDecision.Fade(4_000), plan?.decision)
        assertEquals(180_000L, plan?.outgoingDurationMs)
        assertEquals(0, h.queue.index)
    }

    @Test
    fun `plans a hard cut inside an album played in order`() {
        val h = EngineHarness("ABCD", crossfade = fake, crossfadeSettings = { on })
        h.begin("ABCD")

        assertEquals(CrossfadeDecision.HardCut, runBlocking { fake.host.planNext() }?.decision)
    }

    @Test
    fun `plans a hard cut when crossfade is off`() {
        playing(settings = CrossfadeSettings(enabled = false))

        assertEquals(CrossfadeDecision.HardCut, runBlocking { fake.host.planNext() }?.decision)
    }

    @Test
    fun `plans a hard cut on repeat one`() {
        val h = playing()
        h.engine.setRepeat(RepeatMode.ONE)

        assertEquals(CrossfadeDecision.HardCut, runBlocking { fake.host.planNext() }?.decision)
    }

    @Test
    fun `plans nothing after the last song`() {
        val h = playing("AB")
        h.engine.next()

        assertNull(runBlocking { fake.host.planNext() })
    }

    @Test
    fun `a seek by the owner makes the end of the song a hard cut and asks for a new plan`() {
        val h = playing()
        val before = fake.invalidations

        h.engine.exposed.seekTo(30_000L)

        assertTrue(fake.invalidations > before)
        assertEquals(CrossfadeDecision.HardCut, runBlocking { fake.host.planNext() }?.decision)
    }

    @Test
    fun `a new song clears the seek`() {
        val h = playing()
        h.engine.exposed.seekTo(30_000L)

        h.engine.next()

        assertEquals(CrossfadeDecision.Fade(4_000), runBlocking { fake.host.planNext() }?.decision)
    }

    @Test
    fun `queue changes and new settings ask the crossfade engine for a new plan`() {
        val h = playing()
        val before = fake.invalidations

        h.engine.addToQueue(listOf(ref('Z')))
        h.engine.setShuffle(true)
        h.engine.next()
        h.engine.crossfadeSettingsChanged()

        assertEquals(before + 4, fake.invalidations)
    }

    @Test
    fun `pausing and resuming reaches the crossfade engine`() {
        val h = playing()

        h.state.listener?.onIsPlayingChanged(false)
        h.state.listener?.onIsPlayingChanged(true)

        assertEquals(listOf(true, false, true), fake.playing)
    }

    @Test
    fun `a crossfade writes one row per track and the incoming row does not count the overlap`() {
        val h = playing()

        h.fade(beforeMs = 6_000, fadeMs = 4_000)
        h.time += 10_000
        h.engine.release()

        assertEquals(2, h.history.events.size)
        val (outgoing, incoming) = h.history.events
        assertEquals(ref('A').songId, outgoing.songId)
        assertEquals(EndedBy.COMPLETED, outgoing.endedBy)
        assertEquals(180_000L, outgoing.listenedMs)
        assertEquals(ref('B').songId, incoming.songId)
        assertEquals(StartedBy.AUTO, incoming.startedBy)
        assertEquals(EndedBy.STOPPED, incoming.endedBy)
        assertEquals(10_000L, incoming.listenedMs)
    }

    @Test
    fun `pausing in the middle of a fade does not make the incoming row count the overlap`() {
        val h = playing()
        h.time += 6_000
        h.startFade()

        h.secondState.listener?.onIsPlayingChanged(false)
        h.time += 3_000
        h.secondState.listener?.onIsPlayingChanged(true)
        h.time += 1_000
        runBlocking { fake.host.fadeFinished() }
        h.time += 2_000
        h.engine.release()

        assertEquals(
            2_000L,
            h.history.events
                .last()
                .listenedMs,
        )
    }

    @Test
    fun `a fade that ends while paused starts the incoming clock at the next play`() {
        val h = playing()
        h.secondState.playing = false
        h.secondState.itemCount = 1
        runBlocking { fake.host.fadeStarted(h.secondPlayer) }
        h.time += 4_000
        runBlocking { fake.host.fadeFinished() }
        h.time += 7_000

        h.secondState.listener?.onIsPlayingChanged(true)
        h.time += 5_000
        h.engine.release()

        assertEquals(
            5_000L,
            h.history.events
                .last()
                .listenedMs,
        )
    }

    @Test
    fun `the queue moves to the incoming song and the session is handed the new player`() {
        val h = playing()

        h.fade()

        assertEquals(1, h.queue.index)
        assertEquals(ref('B'), h.queue.current?.ref)
        assertSame(h.secondPlayer, h.engine.activePlayer)
        assertEquals(1, h.sessionPlayers.size)
        assertSame(h.engine.exposed, h.sessionPlayers.single())
    }

    @Test
    fun `the session player reports the incoming position from the start of the fade`() {
        val h = playing()
        h.secondState.position = 1_234

        h.startFade()

        assertEquals(1_234L, h.engine.exposed.currentPosition)
    }

    @Test
    fun `the incoming player holds the window around its song without restarting it`() {
        val h = playing()

        h.fade()

        val calls = h.secondState.calls
        assertTrue(calls.any { it.startsWith("addMediaItems") }, "window was not grown: $calls")
        assertFalse(calls.any { it.startsWith("setMediaItems") }, "the incoming song was restarted: $calls")
        assertEquals(1, h.secondState.itemIndex)
        assertEquals(4, h.secondState.itemCount)
    }

    @Test
    fun `the engine listens to the incoming player and no longer to the outgoing one`() {
        val h = playing()

        h.fade()

        assertTrue(h.state.calls.any { it.startsWith("removeListener") })
        assertTrue(h.secondState.calls.any { it.startsWith("addListener") })
    }

    @Test
    fun `the saved queue follows the fade`() {
        val h = playing()
        val before = saving.changed

        h.fade()

        assertTrue(saving.changed > before)
        assertEquals(1, h.queue.snapshot().index)
    }

    @Test
    fun `next during a fade skips the incoming row with no listening and starts the next one`() {
        val h = playing()
        h.time += 6_000
        h.startFade()
        h.time += 1_500

        h.engine.next()
        h.engine.release()

        val (outgoing, incoming, next) = h.history.events
        assertEquals(EndedBy.COMPLETED, outgoing.endedBy)
        assertEquals(ref('B').songId, incoming.songId)
        assertEquals(EndedBy.SKIPPED, incoming.endedBy)
        assertEquals(0L, incoming.listenedMs)
        assertEquals(ref('C').songId, next.songId)
        assertEquals(StartedBy.NAV, next.startedBy)
    }

    @Test
    fun `a window jump after a fade is a picked row on the incoming player (ADR 0032)`() {
        val h = playing("ABCDE")
        h.fade()
        h.time += 20_000

        h.engine.exposed.seekTo(h.secondState.itemIndex + 2, 0L)
        h.engine.release()

        assertEquals(3, h.queue.index)
        val (_, skipped, picked) = h.history.events
        assertEquals(EndedBy.SKIPPED, skipped.endedBy)
        assertEquals(20_000L, skipped.listenedMs)
        assertEquals(ref('D').songId, picked.songId)
        assertEquals(StartedBy.PICKED, picked.startedBy)
    }

    @Test
    fun `previous after a fade restarts the incoming song as stopped plus a nav row (ADR 0033)`() {
        val h = playing()
        h.fade()
        h.time += 20_000
        h.secondState.position = 20_000

        h.engine.previous()
        h.engine.release()

        val (_, stopped, restarted) = h.history.events
        assertEquals(ref('B').songId, stopped.songId)
        assertEquals(EndedBy.STOPPED, stopped.endedBy)
        assertEquals(20_000L, stopped.listenedMs)
        assertEquals(ref('B').songId, restarted.songId)
        assertEquals(StartedBy.NAV, restarted.startedBy)
    }

    @Test
    fun `release releases the crossfade engine`() {
        val h = playing()

        h.engine.release()

        assertTrue(fake.released)
    }
}
