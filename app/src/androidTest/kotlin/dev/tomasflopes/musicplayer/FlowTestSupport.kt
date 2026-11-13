package dev.tomasflopes.musicplayer

import android.Manifest
import android.app.UiAutomation
import android.content.Context
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import dev.tomasflopes.musicplayer.core.model.ports.LibraryScanController
import dev.tomasflopes.musicplayer.core.model.ports.MusicRoot
import dev.tomasflopes.musicplayer.core.model.ports.ScanPhase
import dev.tomasflopes.musicplayer.core.model.ports.SettingsRepository
import dev.tomasflopes.musicplayer.core.model.ports.SongReader
import dev.tomasflopes.musicplayer.core.tags.SampleLibrary
import dev.tomasflopes.musicplayer.playback.PlaybackController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertNotEquals

internal const val UI_WAIT_MS = 10_000L
internal const val PLAY_WAIT_MS = 5_000L
private const val SCAN_WAIT_MS = 30_000L
private const val POLL_MS = 100L
private const val SAMPLE_MIN = 20

/** The ports the flow tests use to put the app in a known state; the screens themselves are driven by [Screen]. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface FlowGraph {
    fun settings(): SettingsRepository

    fun scanner(): LibraryScanController

    fun songs(): SongReader

    fun playback(): PlaybackController
}

/**
 * Reads and clicks what is on screen through the accessibility tree that `UiAutomation` sees. Compose publishes
 * its text and content descriptions there, so this needs no UI-test dependency. Unverified on a device.
 */
internal class Screen {
    private val automation: UiAutomation =
        InstrumentationRegistry
            .getInstrumentation()
            .uiAutomation

    /** The first node on screen whose text or content description is exactly [label]. */
    fun find(label: String): AccessibilityNodeInfo? = nodes().firstOrNull { label in it.labels() }

    fun waitFor(
        label: String,
        timeoutMs: Long = UI_WAIT_MS,
    ): AccessibilityNodeInfo = until("'$label' on screen", timeoutMs) { find(label) }

    /** Polls [probe] until it returns something, or fails after [timeoutMs]. */
    fun <T : Any> until(
        what: String,
        timeoutMs: Long,
        probe: () -> T?,
    ): T {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() <= deadline) {
            probe()?.let { return it }
            Thread.sleep(POLL_MS)
        }
        throw AssertionError("Timed out after $timeoutMs ms waiting for $what")
    }

    /** Clicks [node], or the nearest parent that can be clicked (Compose puts the click on the merged button). */
    fun click(node: AccessibilityNodeInfo) {
        var target: AccessibilityNodeInfo? = node
        while (target != null && !target.isClickable) {
            target = target.parent
        }
        val clickable = checkNotNull(target) { "nothing clickable at or above ${node.labels()}" }
        check(clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)) { "the click was refused" }
    }

    private fun nodes(): Sequence<AccessibilityNodeInfo> =
        automation.rootInActiveWindow
            ?.descendants()
            .orEmpty()

    private fun AccessibilityNodeInfo.descendants(): Sequence<AccessibilityNodeInfo> =
        sequence {
            yield(this@descendants)
            for (index in 0 until childCount) {
                getChild(index)?.let { yieldAll(it.descendants()) }
            }
        }

    private fun AccessibilityNodeInfo.labels(): List<String> =
        listOfNotNull(text?.toString(), contentDescription?.toString())
}

/**
 * Puts the emulator in the state a first run ends in: audio permission granted, the generated sample folder
 * (`scripts/push-samples.sh`) saved as the music folder and scanned. The saved folder is put back afterwards.
 * Run it on the emulator only: a scan limited to the sample folder retires every song outside it (they come
 * back with the next scan of their own folder).
 */
internal class FlowRig {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var graph: FlowGraph
    private var previousRoot: MusicRoot? = null

    val screen = Screen()

    /** Every title the scan found in the sample folder: what the mini-player may legitimately show. */
    var sampleTitles: Set<String> = emptySet()
        private set

    val playback: PlaybackController get() = graph.playback()

    fun prepare() {
        runBlocking {
            InstrumentationRegistry
                .getInstrumentation()
                .uiAutomation
                .grantRuntimePermission(context.packageName, Manifest.permission.READ_MEDIA_AUDIO)
            onMain { graph = EntryPointAccessors.fromApplication(context, FlowGraph::class.java) }
            previousRoot =
                graph
                    .settings()
                    .musicRoot
                    .first()
            graph.settings().setMusicRoot(MusicRoot("content://test", "external_primary", SampleLibrary.ROOT + "/"))
            graph.scanner().requestScan()
            val songs =
                withTimeoutOrNull(SCAN_WAIT_MS) {
                    graph
                        .songs()
                        .observeSongs()
                        .first { it.size >= SAMPLE_MIN }
                }
            checkNotNull(songs) { "no sample songs after the scan: run scripts/push-samples.sh" }
            sampleTitles = songs.map { it.title }.toSet()
            val phase =
                graph
                    .scanner()
                    .progress
                    .value
                    .phase
            assertNotEquals(ScanPhase.FAILED, phase)
        }
    }

    fun restore() {
        runBlocking {
            onMain {
                if (playback.state.value.isPlaying) {
                    playback.togglePlayPause()
                }
                playback.setShuffle(false)
            }
            previousRoot?.let {
                graph.settings().setMusicRoot(it)
                graph.scanner().requestScan()
            }
        }
    }

    private suspend fun <T> onMain(block: () -> T): T = withContext(Dispatchers.Main) { block() }
}
