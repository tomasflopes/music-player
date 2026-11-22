package dev.tomasflopes.musicplayer.playback.crossfade

import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.tomasflopes.musicplayer.core.model.AlbumId
import dev.tomasflopes.musicplayer.core.model.ArtistId
import dev.tomasflopes.musicplayer.core.model.Song
import dev.tomasflopes.musicplayer.core.model.SongId
import dev.tomasflopes.musicplayer.core.model.crossfade.CrossfadeDecision
import dev.tomasflopes.musicplayer.core.model.crossfade.CrossfadeSettings
import dev.tomasflopes.musicplayer.core.model.crossfade.Transition
import dev.tomasflopes.musicplayer.core.model.crossfade.TransitionCause
import dev.tomasflopes.musicplayer.core.model.crossfade.TransitionStyle
import dev.tomasflopes.musicplayer.core.model.transition.BeatGrid
import dev.tomasflopes.musicplayer.core.model.transition.BeatMatchTechnique
import dev.tomasflopes.musicplayer.core.model.transition.BeatPhase
import dev.tomasflopes.musicplayer.core.model.transition.CrossfadeTechnique
import dev.tomasflopes.musicplayer.core.model.transition.TransitionConfig
import dev.tomasflopes.musicplayer.core.model.transition.TransitionPlan
import dev.tomasflopes.musicplayer.core.model.transition.TransitionPlanner
import dev.tomasflopes.musicplayer.core.model.transition.TransitionProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.abs

/**
 * The DJ overlap on real ExoPlayers, in `deviceCheck` (ADR 0009, 0092): two generated click tracks at 90 and 92 BPM
 * (`testdata/transitions`, `scripts/gen-transition-mp3s.sh`), planned by the real planner and run by the real
 * [TwoPlayerCrossfadeEngine]. The incoming song must start on the beat of the outgoing one, within [TOLERANCE_MS], the
 * volumes must follow the plan, and both players must end at speed 1.0.
 *
 * The two tempos are 2.2 % apart, so the check widens the slice 1 tolerance (1.5 %) to 3 % for itself; with the
 * defaults the planner would answer with a plain crossfade, which is the right thing for the app.
 *
 * NOT RUN: written without an emulator or a phone, never compiled or executed. Run `./gradlew deviceCheck` (with
 * `ANDROID_SERIAL` set to the emulator) before trusting any of it. The owner runs it and reports back.
 */
@RunWith(AndroidJUnit4::class)
class TransitionDeviceCheckTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val attributes =
        AudioAttributes
            .Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val config = TransitionConfig(unstretchedTolerance = 0.03)
    private lateinit var first: ExoPlayer
    private lateinit var second: ExoPlayer
    private lateinit var engine: TwoPlayerCrossfadeEngine

    /** The playback engine's part: plans once and notes both players at the hand-over. */
    private class RecordingHost(
        var active: ExoPlayer,
        private val plan: PlannedTransition,
    ) : CrossfadeHost {
        @Volatile var retiring: ExoPlayer? = null

        @Volatile var outgoingAtStart = -1L

        @Volatile var incomingAtStart = -1L

        @Volatile var finished = 0
        private var planned = false

        override val activePlayer: ExoPlayer get() = active

        override suspend fun planNext(): PlannedTransition? = if (planned) null else plan.also { planned = true }

        override suspend fun fadeStarted(incoming: ExoPlayer) {
            outgoingAtStart = active.currentPosition
            incomingAtStart = incoming.currentPosition
            retiring = active
            active = incoming
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
            engine = TwoPlayerCrossfadeEngine(scope, { second }, attributes, config)
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
    fun theIncomingSongStartsOnTheBeatOfTheOutgoingOne() {
        val outgoingFile = asset("click90_40s_cbr_44100.mp3")
        val incomingFile = asset("click92_40s_cbr_44100.mp3")
        val outgoingProfile = profile(90.0, introEndMs = 20_000, outroStartMs = 20_000)
        val incomingProfile = profile(92.0, introEndMs = 20_000, outroStartMs = 20_000)
        val plan = plan(outgoingProfile, incomingProfile)
        val decision = CrossfadeDecision.Fade(plan.overlapMs)
        val fallback = CrossfadeDecision.Fade(FALLBACK_MS)
        val planned = PlannedTransition(item(incomingFile), decision, DURATION_MS, plan, fallback)
        val host = RecordingHost(first, planned)

        onMain {
            first.setMediaItem(item(outgoingFile), (plan.switchPointMs - LEAD_MS).coerceAtLeast(0))
            first.prepare()
            first.play()
            engine.attach(host)
            engine.invalidate()
        }
        waitFor { host.finished == 1 }

        val error =
            BeatPhase.errorMs(
                BeatGrid.of(outgoingProfile, config),
                host.outgoingAtStart.toDouble(),
                BeatGrid.of(incomingProfile, config),
                host.incomingAtStart.toDouble(),
            )
        assertTrue("incoming started ${error.toLong()} ms off the beat", abs(error) <= TOLERANCE_MS)
        val late = abs(host.outgoingAtStart - plan.switchPointMs)
        assertTrue("overlap started $late ms from the switch point", late <= TOLERANCE_MS)
        onMain {
            assertEquals("incoming ends at full volume", 1f, host.active.volume, 0f)
            assertEquals("incoming ends at speed 1.0", 1f, host.active.playbackParameters.speed, 0f)
            assertEquals("outgoing ends at speed 1.0", 1f, checkNotNull(host.retiring).playbackParameters.speed, 0f)
            assertEquals("the retired player is empty", 0, first.mediaItemCount)
        }
    }

    private fun plan(
        outgoing: TransitionProfile,
        incoming: TransitionProfile,
    ): TransitionPlan.Overlap {
        val planner = TransitionPlanner(listOf(BeatMatchTechnique(), CrossfadeTechnique()), config)
        val settings = CrossfadeSettings(enabled = true, durationMs = 4_000, style = TransitionStyle.DJ_MIX)
        val transition = Transition(song(1), song(2), TransitionCause.END_OF_SONG, shuffled = true, repeatOne = false)
        val plan = planner.plan(settings, transition, outgoing, incoming)
        assertNotNull("the planner made no overlap: $plan", plan as? TransitionPlan.Overlap)
        return plan as TransitionPlan.Overlap
    }

    private fun profile(
        tempo: Double,
        introEndMs: Long,
        outroStartMs: Long,
    ) = TransitionProfile(tempo, 0.0, 0.9f, 0, introEndMs, outroStartMs, DURATION_MS - 500)

    private fun song(id: Long) =
        Song(
            id = SongId(id),
            title = "Click $id",
            artistId = ArtistId(id),
            artistName = "Artist",
            albumId = AlbumId(id),
            albumTitle = "Album $id",
            albumArtist = "Artist",
            trackNumber = 1,
            discNumber = 1,
            durationMs = DURATION_MS,
            volumeName = "external_primary",
            path = "Music/$id.mp3",
            fileName = "$id.mp3",
            folderPath = "Music",
            dateAddedEpochSec = 0,
            contentUri = "content://media/$id",
            hasCover = false,
        )

    private fun item(file: File) = MediaItem.fromUri(Uri.fromFile(file))

    /** The test APK's asset, copied to the cache so ExoPlayer can read it as a file. */
    private fun asset(name: String): File =
        File(context.cacheDir, name).also { file ->
            instrumentation.context.assets.open(name).use { input -> file.outputStream().use { input.copyTo(it) } }
        }

    private fun waitFor(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + TIMEOUT_MS
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

    private companion object {
        const val DURATION_MS = 40_000L
        const val LEAD_MS = 4_000L
        const val TOLERANCE_MS = 80L
        const val FALLBACK_MS = 4_000L
        const val SAMPLE_MS = 100L
        const val TIMEOUT_MS = 60_000L
    }
}
