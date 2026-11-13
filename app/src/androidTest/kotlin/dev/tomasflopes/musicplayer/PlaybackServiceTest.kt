package dev.tomasflopes.musicplayer

import android.Manifest
import android.content.ComponentName
import android.content.Context
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import dev.tomasflopes.musicplayer.core.model.Song
import dev.tomasflopes.musicplayer.core.model.config.LibraryConfig
import dev.tomasflopes.musicplayer.core.model.log.PlaySource
import dev.tomasflopes.musicplayer.core.model.ports.MusicRoot
import dev.tomasflopes.musicplayer.core.model.ports.SongReader
import dev.tomasflopes.musicplayer.core.model.ref
import dev.tomasflopes.musicplayer.core.tags.Id3TagReader
import dev.tomasflopes.musicplayer.core.tags.SampleLibrary
import dev.tomasflopes.musicplayer.data.db.MusicDatabase
import dev.tomasflopes.musicplayer.data.scan.ContentResolverTagSource
import dev.tomasflopes.musicplayer.data.scan.LibraryScanner
import dev.tomasflopes.musicplayer.data.scan.MediaStoreSongLister
import dev.tomasflopes.musicplayer.playback.PlayRequest
import dev.tomasflopes.musicplayer.playback.PlaybackController
import dev.tomasflopes.musicplayer.playback.PlaybackService
import dev.tomasflopes.musicplayer.playback.queue.RepeatMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Plays the generated sample library (pushed by `scripts/push-samples.sh`) through the real service.
 * Runs on the emulator: `ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest`.
 */
@RunWith(AndroidJUnit4::class)
class PlaybackServiceTest {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Graph {
        fun controller(): PlaybackController

        fun songs(): SongReader

        fun db(): MusicDatabase
    }

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var playback: PlaybackController
    private lateinit var songs: List<Song>
    private lateinit var second: MediaController

    @Before
    fun setUp() =
        runBlocking {
            InstrumentationRegistry
                .getInstrumentation()
                .uiAutomation
                .grantRuntimePermission(context.packageName, Manifest.permission.READ_MEDIA_AUDIO)
            lateinit var graph: Graph
            onMain { graph = EntryPointAccessors.fromApplication(context, Graph::class.java) }
            val cr = context.contentResolver
            LibraryScanner(
                lister = MediaStoreSongLister(cr),
                tagSources = { file -> ContentResolverTagSource(cr, file) },
                tagReader = Id3TagReader(),
                db = graph.db(),
                config = LibraryConfig(),
            ).scan(MusicRoot("content://test", "external_primary", SampleLibrary.ROOT + "/")) {}
            songs = graph.songs().observeSongs().first()
            assertTrue("push the samples first: scripts/push-samples.sh", songs.size >= SAMPLE_MIN)
            onMain {
                playback = graph.controller()
                val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
                second = MediaController.Builder(context, token).buildAsync().get()
            }
        }

    @After
    fun tearDown() {
        runBlocking { onMain { second.pause() } }
        runBlocking { onMain { playback.setShuffle(false) } }
    }

    private fun play(
        count: Int,
        shuffle: Boolean = false,
        start: Int = 0,
    ) = playback.play(
        PlayRequest(songs.take(count).map { it.ref }, start, shuffle, PlaySource.SONGS, picked = true),
    )

    private suspend fun <T> onMain(block: () -> T): T = withContext(Dispatchers.Main) { block() }

    private fun until(
        what: String,
        condition: () -> Boolean,
    ) = runBlocking {
        withTimeout(WAIT_MS) {
            while (!onMain(condition)) kotlinx.coroutines.delay(POLL_MS)
        }
    }

    private fun current() = playback.state.value.current

    @Test
    fun threeSongsPlayWithTheirTags() {
        play(3)

        until("playing") { playback.state.value.isPlaying && current() != null }
        val song = songs.first()
        assertEquals(song.title, current()?.title)
        runBlocking { onMain { assertEquals(song.title, second.mediaMetadata.title?.toString()) } }
        runBlocking { onMain { assertEquals(song.artistName, second.mediaMetadata.artist?.toString()) } }
    }

    @Test
    fun nextTwiceLandsOnTheThirdSong() {
        play(3)
        until("playing") { playback.state.value.isPlaying }

        playback.next()
        until("second") { current()?.id == songs[1].id }
        playback.next()
        until("third") { current()?.id == songs[2].id }
    }

    @Test
    fun aLongRequestKeepsAtMostFortyOneItemsInThePlayer() {
        playback.play(
            PlayRequest(List(LONG_REQUEST) { songs[it % songs.size].ref }, 0, false, PlaySource.SONGS, picked = false),
        )
        until("playing") { playback.state.value.isPlaying }

        runBlocking { onMain { assertTrue("${second.mediaItemCount} items", second.mediaItemCount <= 41) } }
    }

    @Test
    fun tenQuickNextsLandOnTheEleventhSong() {
        play(songs.size)
        until("playing") { playback.state.value.isPlaying }

        repeat(10) { playback.next() }

        until("eleventh") { current()?.id == songs[10].id }
    }

    @Test
    fun shuffleMidSongKeepsTheCurrentSongRunningAndChangesTheNextOne() {
        play(songs.size)
        until("playing") { playback.state.value.isPlaying }
        val before = playback.state.value
        val nextBefore = before.upcoming.first().id

        playback.setShuffle(true)

        until("shuffled") { playback.state.value.shuffle }
        assertEquals(before.current?.id, current()?.id)
        assertTrue(playback.state.value.isPlaying)
        assertTrue(playback.state.value.positionMs >= before.positionMs)
        assertNotEquals(
            nextBefore,
            playback.state.value.upcoming
                .first()
                .id,
        )
    }

    @Test
    fun repeatOneLoopsTheSameSong() {
        play(2)
        until("playing") { playback.state.value.isPlaying }
        playback.cycleRepeatMode()
        playback.cycleRepeatMode()
        until("repeat one") { playback.state.value.repeatMode == RepeatMode.ONE }
        val song = current()?.id

        runBlocking { onMain { second.seekTo((second.duration - NEAR_END_MS).coerceAtLeast(0)) } }

        until("looped") { playback.state.value.positionMs < LOOP_RESTART_MS && playback.state.value.isPlaying }
        assertEquals(song, current()?.id)
    }

    @Test
    fun repeatAllGoesFromTheLastSongToTheFirst() {
        play(2, start = 1)
        until("playing") { playback.state.value.isPlaying }
        playback.cycleRepeatMode()
        until("repeat all") { playback.state.value.repeatMode == RepeatMode.ALL }

        runBlocking { onMain { second.seekTo((second.duration - NEAR_END_MS).coerceAtLeast(0)) } }

        until("wrapped") { current()?.id == songs[0].id }
    }

    @Test
    fun aShuffleCommandFromAnotherControllerShowsInPlaybackState() {
        play(songs.size)
        until("playing") { playback.state.value.isPlaying }

        runBlocking { onMain { second.shuffleModeEnabled = true } }

        until("shuffle on") { playback.state.value.shuffle }
        runBlocking { onMain { assertTrue(second.shuffleModeEnabled) } }
    }

    private companion object {
        const val SAMPLE_MIN = 20
        const val WAIT_MS = 5_000L
        const val POLL_MS = 50L
        const val LONG_REQUEST = 100
        const val NEAR_END_MS = 400L
        const val LOOP_RESTART_MS = 300L
    }
}
