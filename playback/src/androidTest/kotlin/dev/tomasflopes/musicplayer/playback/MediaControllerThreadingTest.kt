package dev.tomasflopes.musicplayer.playback

import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.tomasflopes.musicplayer.core.model.AlbumId
import dev.tomasflopes.musicplayer.core.model.ArtistId
import dev.tomasflopes.musicplayer.core.model.Song
import dev.tomasflopes.musicplayer.core.model.SongId
import dev.tomasflopes.musicplayer.core.model.SongRef
import dev.tomasflopes.musicplayer.core.model.log.PlaySource
import dev.tomasflopes.musicplayer.core.model.ports.SongReader
import dev.tomasflopes.musicplayer.core.model.queue.RefillSeed
import dev.tomasflopes.musicplayer.core.taste.shuffle.PlainShuffle
import dev.tomasflopes.musicplayer.playback.queue.PlayQueue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * Commands may be called from any thread (ADR 0083): the refiller sends `appendBatch` from a Default worker, and
 * Media3's MediaController throws when it is called off its application thread. Runs in `deviceCheck`.
 */
@RunWith(AndroidJUnit4::class)
class MediaControllerThreadingTest {
    private lateinit var controller: PlaybackController

    @Before
    fun connect() {
        RecordingSessionService.received.clear()
        val context = InstrumentationRegistry.getInstrumentation().context
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            controller =
                MediaControllerPlaybackController(
                    context,
                    PlayQueue(PlainShuffle(), Random.Default),
                    emptySongReader(),
                    PlaybackConfig(),
                    CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
                    RecordingSessionService::class.java,
                )
            // Sent from the main thread before the controller connects: it arrives once the session is connected.
            controller.addToQueue(refs(1))
        }
        assertEquals(PlaybackCommands.addToQueue.customAction, next())
    }

    @After
    fun drain() = RecordingSessionService.received.clear()

    @Test
    fun appendBatch_from_a_default_worker_reaches_the_session() {
        val failure = onDefault { controller.appendBatch(refs(2, 3), RefillSeed.Song(1), 1, PlaySource.RADIO) }

        assertEquals(null, failure)
        assertEquals(PlaybackCommands.appendBatch.customAction, next())
    }

    @Test
    fun addToQueue_from_a_default_worker_reaches_the_session() {
        val failure = onDefault { controller.addToQueue(refs(4)) }

        assertEquals(null, failure)
        assertEquals(PlaybackCommands.addToQueue.customAction, next())
    }

    @Test
    fun a_command_from_the_main_thread_still_reaches_the_session() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            assertEquals(Looper.getMainLooper(), Looper.myLooper())
            controller.playNext(refs(5))
        }

        assertEquals(PlaybackCommands.playNext.customAction, next())
    }

    private fun refs(vararg ids: Long) = ids.map { SongRef(SongId(it), ArtistId(1)) }

    private fun next(): String? = RecordingSessionService.received.poll(TIMEOUT_S, TimeUnit.SECONDS)

    /** Runs [block] on a Default worker; returns what it threw, or null. */
    private fun onDefault(block: () -> Unit): Throwable? =
        runBlocking {
            var thrown: Throwable? = null
            CoroutineScope(Dispatchers.Default).launch { thrown = runCatching(block).exceptionOrNull() }.join()
            thrown
        }

    private fun emptySongReader(): SongReader =
        object : SongReader {
            override fun observeSongs() = emptyFlow<List<Song>>()

            override fun observeAddedSince(epochSec: Long) = emptyFlow<List<Song>>()

            override fun observeAlbumSongs(id: AlbumId) = emptyFlow<List<Song>>()

            override fun observeArtistSongs(id: ArtistId) = emptyFlow<List<Song>>()

            override fun observeFeaturedOn(id: ArtistId) = emptyFlow<List<Song>>()

            override fun observeFolderSongs(folderPath: String) = emptyFlow<List<Song>>()

            override suspend fun songsByIds(ids: List<SongId>) = emptyList<Song>()

            override suspend fun allSongRefs() = emptyList<SongRef>()
        }

    private companion object {
        const val TIMEOUT_S = 10L
    }
}
