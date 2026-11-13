package dev.tomasflopes.musicplayer.data.scan

import android.Manifest
import android.content.Context
import android.os.Environment
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.tomasflopes.musicplayer.core.model.AlbumOrder
import dev.tomasflopes.musicplayer.core.model.config.LibraryConfig
import dev.tomasflopes.musicplayer.core.model.log.EndedBy
import dev.tomasflopes.musicplayer.core.model.log.PlayEvent
import dev.tomasflopes.musicplayer.core.model.log.PlaySource
import dev.tomasflopes.musicplayer.core.model.log.StartedBy
import dev.tomasflopes.musicplayer.core.model.ports.MusicRoot
import dev.tomasflopes.musicplayer.core.model.ports.ScanPhase
import dev.tomasflopes.musicplayer.core.model.ports.ScanProgress
import dev.tomasflopes.musicplayer.core.tags.Id3TagReader
import dev.tomasflopes.musicplayer.core.tags.SampleLibrary
import dev.tomasflopes.musicplayer.core.tags.TagReadResult
import dev.tomasflopes.musicplayer.core.tags.TagReader
import dev.tomasflopes.musicplayer.core.tags.TagSource
import dev.tomasflopes.musicplayer.data.db.MusicDatabase
import dev.tomasflopes.musicplayer.data.db.entity.PlayEventEntity
import dev.tomasflopes.musicplayer.data.db.inMemoryDatabase
import dev.tomasflopes.musicplayer.data.repo.RoomAlbumReader
import dev.tomasflopes.musicplayer.data.repo.RoomSongReader
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Scans the generated sample library pushed to the emulator by `scripts/push-samples.sh`
 * (`/sdcard/Music/Sample` and `/sdcard/Recordings`). Needs READ_MEDIA_AUDIO, granted by the test manifest.
 */
@RunWith(AndroidJUnit4::class)
class LibraryScannerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val root =
        MusicRoot(
            "content://test",
            volumeName = "external_primary",
            relativePath =
                SampleLibrary.ROOT + "/",
        )
    private val inRoot = SampleLibrary.files.filter { it.inRoot }
    private lateinit var db: MusicDatabase
    private lateinit var reads: CountingTagReader
    private lateinit var scanner: LibraryScanner
    private val progress = mutableListOf<ScanProgress>()

    /** Counts tag reads, so "a second scan reads no tags" can be asserted. */
    private class CountingTagReader : TagReader {
        private val reader = Id3TagReader()
        var count = 0

        override fun read(source: TagSource) = reader.read(source).also { count++ }
    }

    @Before
    fun setUp() {
        db = inMemoryDatabase()
        reads = CountingTagReader()
        scanner =
            LibraryScanner(
                lister = MediaStoreSongLister(context.contentResolver),
                tagSources = { file -> ContentResolverTagSource(context.contentResolver, file) },
                tagReader = reads,
                db = db,
                config = LibraryConfig(scanBatchSize = 500),
            )
        InstrumentationRegistry
            .getInstrumentation()
            .uiAutomation
            .grantRuntimePermission(context.packageName, Manifest.permission.READ_MEDIA_AUDIO)
        val samples = File(Environment.getExternalStorageDirectory(), SampleLibrary.ROOT)
        assertTrue("push the samples first: scripts/push-samples.sh", samples.isDirectory)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun scan() = scanner.scan(root) { progress += it }

    @Test
    fun scanFindsExactlyTheFilesInsideTheRoot() =
        runTest {
            scan()

            val songs = RoomSongReader(db).observeSongs().first()
            assertEquals(inRoot.size, songs.size)
            assertTrue(songs.none { it.fileName == "voice note.mp3" })
            assertEquals(ScanPhase.DONE, progress.last().phase)
        }

    @Test
    fun everySongReadsBackWithItsExpectedTags() =
        runTest {
            scan()

            val byPath = RoomSongReader(db).observeSongs().first().associateBy { it.path }
            inRoot.forEach { sample ->
                val song = checkNotNull(byPath[sample.path]) { sample.path }
                val expected = sample.expected
                val fileName = sample.path.substringAfterLast('/')
                val expectedTitle = expected.title ?: fileName.substringBeforeLast('.')
                assertEquals(sample.path, expectedTitle, song.title)
                assertEquals(sample.path, expected.artist ?: "Unknown artist", song.artistName)
                assertEquals(sample.path, expected.album, song.albumTitle)
                assertEquals(sample.path, expected.trackNumber, song.trackNumber)
                assertEquals(sample.path, expected.discNumber, song.discNumber)
                assertEquals(sample.path, expected.hasCover, song.hasCover)
            }
        }

    @Test
    fun albumOneReadsBackInDiscThenTrackOrder() =
        runTest {
            scan()

            val albums = RoomAlbumReader(db).observeAlbums().first()
            val albumOne = albums.single { it.title == "Album One" }
            val songs = RoomSongReader(db).observeAlbumSongs(albumOne.id).first()

            assertEquals(listOf("Opening", "Second", "Third", "Fourth", "Fifth", "Sixth"), songs.map { it.title })
            assertEquals(songs, songs.sortedWith(AlbumOrder))
        }

    @Test
    fun sameNameIsTwoAlbums() =
        runTest {
            scan()

            val sameName = RoomAlbumReader(db).observeAlbums().first().filter { it.title == "Same Name" }

            assertEquals(setOf("Sample A", "Sample B"), sameName.map { it.albumArtist }.toSet())
        }

    @Test
    fun secondScanReadsNoTags() =
        runTest {
            scan()
            val firstScanReads = reads.count

            scan()

            assertEquals(inRoot.size, firstScanReads)
            assertEquals(firstScanReads, reads.count)
        }

    @Test
    fun aMissingFileIsRetiredAndKeepsItsHistory() =
        runTest {
            scan()
            val songs = RoomSongReader(db).observeSongs().first()
            val gone = songs.first()
            db.playEventDao().insert(
                PlayEventEntity.from(
                    PlayEvent(gone.id, 1, 1, 1, EndedBy.COMPLETED, PlaySource.ALBUM, StartedBy.PICKED, shuffle = false),
                ),
            )

            scanner.scanFiles(root, scanner.listFiles(root).filter { it.path != gone.path }) { }

            val after = RoomSongReader(db).observeSongs().first()
            assertEquals(songs.size - 1, after.size)
            assertTrue(after.none { it.id == gone.id })
            assertEquals(1, db.playEventDao().observeCount().first())
            assertEquals(
                gone.id.value,
                db
                    .playEventDao()
                    .observeRecent(1)
                    .first()
                    .single()
                    .songId,
            )
        }

    @Test
    fun aFileWhoseTagsFailStillAppearsTitledByFileName() =
        runTest {
            val failing =
                LibraryScanner(
                    lister = MediaStoreSongLister(context.contentResolver),
                    tagSources = { file -> ContentResolverTagSource(context.contentResolver, file) },
                    tagReader =
                        object : TagReader {
                            override fun read(source: TagSource) = TagReadResult.Failure("broken")
                        },
                    db = db,
                    config = LibraryConfig(),
                )

            failing.scan(root) { }

            val songs = RoomSongReader(db).observeSongs().first()
            assertEquals(inRoot.size, songs.size)
            assertTrue(songs.any { it.title == "c opening" && it.artistName == "Unknown artist" })
        }

    @Test
    fun batchesAreAtMostTheConfiguredSize() =
        runTest {
            val small =
                LibraryScanner(
                    lister = MediaStoreSongLister(context.contentResolver),
                    tagSources = { file -> ContentResolverTagSource(context.contentResolver, file) },
                    tagReader = Id3TagReader(),
                    db = db,
                    config = LibraryConfig(scanBatchSize = 7),
                )
            val indexing = mutableListOf<ScanProgress>()

            small.scan(root) { if (it.phase == ScanPhase.INDEXING) indexing += it }

            val batchSizes = indexing.map { it.done }.zipWithNext { a, b -> b - a }
            assertTrue(indexing.toString(), batchSizes.isNotEmpty() && batchSizes.all { it in 1..7 })
            assertEquals(inRoot.size, indexing.last().done)
        }
}
