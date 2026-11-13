package dev.tomasflopes.musicplayer.core.tags.audit

import dev.tomasflopes.musicplayer.core.tags.RawTags
import dev.tomasflopes.musicplayer.core.tags.SampleLibrary
import dev.tomasflopes.musicplayer.core.tags.SampleLibraryGenerator
import dev.tomasflopes.musicplayer.core.tags.TagReadResult
import dev.tomasflopes.musicplayer.core.tags.TagVersion
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class LibraryAuditTest {
    @TempDir
    lateinit var dir: File

    private fun auditSamples(): AuditReport {
        SampleLibraryGenerator.writeTo(dir)
        return LibraryAudit.audit(dir)
    }

    private fun entry(
        path: String,
        title: String?,
        vararg warnings: String,
    ) = AuditEntry(
        path,
        TagReadResult.Success(
            RawTags(title, "Artist", null, "Album", null, null, null, null, false, TagVersion.ID3V2_3),
            warnings.toList(),
        ),
    )

    @Test
    fun `every sample file is read, none fails`() {
        val report = auditSamples()

        assertEquals(SampleLibrary.files.size, report.entries.size)
        assertEquals(emptyList<AuditEntry>(), report.failures)
    }

    @Test
    fun `version counts equal the sample library's mix`() {
        val expected = SampleLibrary.files.groupingBy { it.expected.version }.eachCount()

        val counts = auditSamples().versionCounts

        assertEquals(expected, counts)
        assertEquals(listOf(1, 1, 1), listOf(TagVersion.ID3V2_2, TagVersion.ID3V1, TagVersion.NONE).map(counts::get))
    }

    @Test
    fun `only the untagged sample misses title, artist and album`() {
        val report = auditSamples()

        assertEquals(listOf(1, 1, 1), listOf(report.missingTitle, report.missingArtist, report.missingAlbum))
    }

    @Test
    fun `paths are relative to the audited folder, in a stable order`() {
        val paths = auditSamples().entries.map { it.path }

        assertEquals(SampleLibrary.files.map { it.path }.sorted(), paths)
    }

    @Test
    fun `non-mp3 files are ignored and the extension check ignores case`() {
        File(dir, "cover.jpg").writeBytes(ByteArray(10))
        File(dir, "LOUD.MP3").writeBytes(ByteArray(10))

        assertEquals(listOf("LOUD.MP3"), LibraryAudit.audit(dir).entries.map { it.path })
    }

    @Test
    fun `failures are listed with their reason`() {
        val failed = AuditEntry("bad.mp3", TagReadResult.Failure("too short"))
        val report = AuditReport(listOf(entry("ok.mp3", "Song"), failed), 1.0)

        assertEquals(listOf("bad.mp3"), report.failures.map { it.path })
        assertTrue(report.summary().contains("bad.mp3: too short"), report.summary())
    }

    @Test
    fun `warnings are counted by kind and multi-value artists get their own count`() {
        val entries =
            listOf(
                entry("a.mp3", "A", "multi-value TPE1"),
                entry("b.mp3", "B", "multi-value TPE1", "skipped compressed TIT2"),
                entry("c.mp3", "C", "multi-value TALB"),
            )

        val report = AuditReport(entries, 1.0)

        val expected = mapOf("multi-value TPE1" to 2, "skipped compressed TIT2" to 1, "multi-value TALB" to 1)
        assertEquals(expected, report.warningCounts)
        assertEquals(2, report.multiValueArtist)
    }

    @Test
    fun `summary states files read and files per second`() {
        val summary = AuditReport(listOf(entry("a.mp3", "A"), entry("b.mp3", "B")), seconds = 0.5).summary()

        assertTrue(summary.contains("Files read: 2"), summary)
        assertTrue(summary.contains("Files per second: 4"), summary)
    }

    @Test
    fun `csv has a header and one row per file, with commas and quotes escaped`() {
        val csv = AuditReport(listOf(entry("a, b.mp3", "Say \"hi\"", "multi-value TPE1")), 1.0).filesCsv().lines()

        val header = "path,version,title,artist,album_artist,album,track,track_total,disc,disc_total,cover,problems"
        assertEquals(header, csv[0])
        assertEquals("\"a, b.mp3\",ID3V2_3,\"Say \"\"hi\"\"\",Artist,,Album,,,,,false,multi-value TPE1", csv[1])
    }
}
