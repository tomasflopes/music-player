package dev.tomasflopes.musicplayer.playback.crossfade

import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.tomasflopes.musicplayer.core.model.crossfade.CrossfadeDecision
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The two-player blend of [TwoPlayerCrossfadeEngine] on a real ExoPlayer pair, in `deviceCheck` (ADR 0009, 0061).
 *
 * NOT RUN: written without an emulator or a phone, never compiled or executed. Run `./gradlew deviceCheck` (with
 * `ANDROID_SERIAL` set to the emulator) before trusting any of it. The audio is two generated, silent, constant-bitrate
 * MP3s, so what is checked is the engine's timing, the volumes it sets and the role swap, not what is heard.
 * A [RecordingHost] stands in for the playback engine and does the one thing that engine does at a fade: it takes the
 * incoming player as its own.
 */
@RunWith(AndroidJUnit4::class)
class TwoPlayerCrossfadeEngineTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val attributes =
        AudioAttributes
            .Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var first: ExoPlayer
    private lateinit var second: ExoPlayer
    private lateinit var engine: TwoPlayerCrossfadeEngine

    private class Sample(
        val outgoingVolume: Float,
        val incomingVolume: Float,
        val outgoingPosition: Long,
        val incomingPosition: Long,
    )

    /** Plays the playback engine's part; every method runs on the main thread, where the engine calls it. */
    private class RecordingHost(
        var active: ExoPlayer,
        private val plan: PlannedTransition,
    ) : CrossfadeHost {
        @Volatile var retiring: ExoPlayer? = null

        @Volatile var remainingAtStartMs = -1L

        @Volatile var incomingVolumeAtStart = -1f

        @Volatile var started = 0

        @Volatile var finished = 0
        private var planned = false

        override val activePlayer: ExoPlayer get() = active

        override suspend fun planNext(): PlannedTransition? = if (planned) null else plan.also { planned = true }

        override suspend fun fadeStarted(incoming: ExoPlayer) {
            remainingAtStartMs = active.duration - active.currentPosition
            incomingVolumeAtStart = incoming.volume
            retiring = active
            active = incoming
            started++
        }

        override suspend fun fadeFinished() {
            finished++
        }
    }

    @Before
    fun setUp() {
        onMain {
            first = ExoPlayer.Builder(context).build().also { it.setAudioAttributes(attributes, false) }
            second = ExoPlayer.Builder(context).build()
            engine = TwoPlayerCrossfadeEngine(scope, { second }, attributes)
        }
    }

    @After
    fun tearDown() {
        onMain {
            engine.release()
            first.release()
            second.release()
        }
        scope.cancel()
    }

    @Test
    fun fadesFromTheRightMomentEndsAtZeroAndOneAndSwapsTheRoles() {
        val host = host(outgoingSeconds = 9, incomingSeconds = 9, fadeMs = FADE_MS)

        val samples = playUntil(host) { it.finished == 1 }

        assertEquals("one fade", 1, host.started)
        assertEquals("one end of fade", 1, host.finished)
        assertTrue("fade started ${host.remainingAtStartMs} ms before the end", host.remainingAtStartMs in window())
        assertEquals("the incoming song starts silent", 0f, host.incomingVolumeAtStart, 0f)
        val midFade = samples.any { it.incomingVolume in 0.05f..0.95f }
        assertTrue("never a mid-fade volume: ${samples.size} samples", midFade)
        samples.zipWithNext().forEach { (before, after) ->
            assertTrue("incoming volume fell", after.incomingVolume >= before.incomingVolume - TOLERANCE)
            assertTrue("outgoing volume rose", after.outgoingVolume <= before.outgoingVolume + TOLERANCE)
        }
        onMain {
            assertEquals("incoming ends at full volume", 1f, host.active.volume, 0f)
            assertEquals("outgoing ends silent", 0f, host.retiring!!.volume, 0f)
            assertSame("the spare player took over", second, host.active)
            assertEquals("the old player is empty", 0, first.mediaItemCount)
            assertFalse("the old player is stopped", first.isPlaying)
            assertTrue("the new player plays on", second.isPlaying)
        }
    }

    @Test
    fun fallsBackToAHardCutWhenTheSecondPlayerIsNotReady() {
        val host = host(outgoingSeconds = 6, incomingSeconds = 6, fadeMs = 2_000, incomingExists = false)

        playUntil(host, timeoutMs = 15_000) { onMain { first.playbackState == Player.STATE_ENDED } }

        assertEquals("no fade started", 0, host.started)
        assertEquals("the outgoing player carried on to its end", Player.STATE_ENDED, onMain { first.playbackState })
    }

    @Test
    fun pausingAndResumingMidFadeStopsAndRestartsBothPlayers() {
        val host = host(outgoingSeconds = 9, incomingSeconds = 9, fadeMs = FADE_MS)
        onMain { startOutgoing() }
        waitFor { host.started == 1 }

        onMain {
            host.active.pause()
            engine.onPlayingChanged(false)
        }
        Thread.sleep(PAUSE_MS)
        val paused = sample(host)
        Thread.sleep(PAUSE_MS)
        val stillPaused = sample(host)
        onMain {
            host.active.play()
            engine.onPlayingChanged(true)
        }
        waitFor { host.finished == 1 }

        assertEquals("incoming moved while paused", paused.incomingPosition, stillPaused.incomingPosition)
        assertEquals("outgoing moved while paused", paused.outgoingPosition, stillPaused.outgoingPosition)
        assertEquals("the fade still ended", 1, host.finished)
        onMain { assertEquals(1f, host.active.volume, 0f) }
    }

    private fun window() = (FADE_MS - SLACK_MS)..(FADE_MS + SLACK_MS)

    private fun host(
        outgoingSeconds: Int,
        incomingSeconds: Int,
        fadeMs: Long,
        incomingExists: Boolean = true,
    ): RecordingHost {
        val outgoing = mp3("outgoing", outgoingSeconds)
        val incoming = if (incomingExists) mp3("incoming", incomingSeconds) else File(context.cacheDir, "missing.mp3")
        val plan =
            PlannedTransition(
                MediaItem.fromUri(android.net.Uri.fromFile(incoming)),
                CrossfadeDecision.Fade(fadeMs),
                outgoingSeconds * MS_PER_SECOND,
            )
        outgoingItem = MediaItem.fromUri(android.net.Uri.fromFile(outgoing))
        return RecordingHost(first, plan).also { hostInUse = it }
    }

    private var outgoingItem: MediaItem? = null
    private var hostInUse: RecordingHost? = null

    private fun startOutgoing() {
        val host = checkNotNull(hostInUse)
        first.setMediaItem(checkNotNull(outgoingItem))
        first.prepare()
        first.play()
        engine.attach(host)
        engine.invalidate()
    }

    /** Starts the outgoing song and samples both players until [done], or fails after [timeoutMs]. */
    private fun playUntil(
        host: RecordingHost,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
        done: (RecordingHost) -> Boolean,
    ): List<Sample> {
        onMain { startOutgoing() }
        val samples = mutableListOf<Sample>()
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!done(host)) {
            assertTrue("timed out after $timeoutMs ms", System.currentTimeMillis() < deadline)
            if (host.started == 1) samples += sample(host)
            Thread.sleep(SAMPLE_MS)
        }
        return samples
    }

    private fun sample(host: RecordingHost): Sample =
        onMain {
            val outgoing = checkNotNull(host.retiring)
            Sample(outgoing.volume, host.active.volume, outgoing.currentPosition, host.active.currentPosition)
        }

    private fun waitFor(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + DEFAULT_TIMEOUT_MS
        while (!condition()) {
            assertTrue("timed out waiting", System.currentTimeMillis() < deadline)
            Thread.sleep(SAMPLE_MS)
        }
    }

    private fun <T> onMain(block: () -> T): T {
        var result: Any? = null
        instrumentation.runOnMainSync { result = block() }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    /** A silent MPEG-1 Layer III file: 128 kbps, 44.1 kHz stereo frames of 417 bytes, each 1152 samples long. */
    private fun mp3(
        name: String,
        seconds: Int,
    ): File {
        val frames = seconds * SAMPLE_RATE / SAMPLES_PER_FRAME
        val bytes = ByteArray(frames * FRAME_BYTES)
        for (frame in 0 until frames) {
            val at = frame * FRAME_BYTES
            bytes[at] = 0xFF.toByte()
            bytes[at + 1] = 0xFB.toByte()
            bytes[at + 2] = 0x90.toByte()
            bytes[at + 3] = 0x00
        }
        return File(context.cacheDir, "crossfade-$name-$seconds.mp3").also { it.writeBytes(bytes) }
    }

    private companion object {
        const val FADE_MS = 3_000L
        const val SLACK_MS = 400L
        const val TOLERANCE = 0.01f
        const val SAMPLE_MS = 100L
        const val PAUSE_MS = 500L
        const val DEFAULT_TIMEOUT_MS = 30_000L
        const val MS_PER_SECOND = 1_000L
        const val SAMPLE_RATE = 44_100
        const val SAMPLES_PER_FRAME = 1_152
        const val FRAME_BYTES = 417
    }
}
