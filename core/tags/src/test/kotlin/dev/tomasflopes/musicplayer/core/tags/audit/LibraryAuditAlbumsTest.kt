package dev.tomasflopes.musicplayer.core.tags.audit

import dev.tomasflopes.musicplayer.core.tags.RawTags
import dev.tomasflopes.musicplayer.core.tags.SampleLibraryGenerator
import dev.tomasflopes.musicplayer.core.tags.TagReadResult
import dev.tomasflopes.musicplayer.core.tags.TagVersion
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class LibraryAuditAlbumsTest {
    @TempDir
    lateinit var dir: File

    private fun tagged(
        path: String,
        title: String? = path.substringAfterLast('/').substringBefore('.'),
        artist: String? = "Artist",
        album: String? = "Album",
        track: Int? = null,
        disc: Int? = null,
    ) = AuditEntry(
        path,
        TagReadResult.Success(
            RawTags(title, artist, null, album, track, null, disc, null, false, TagVersion.ID3V2_3),
            emptyList(),
        ),
    )

    private fun report(vararg entries: AuditEntry) = AuditReport(entries.toList(), 1.0)

    @Test
    fun `sample library groups into fourteen albums, five fully numbered, nine unnumbered`() {
        SampleLibraryGenerator.writeTo(dir)

        val numbering =
            LibraryAudit
                .audit(dir)
                .albums
                .groupingBy { it.numbering }
                .eachCount()

        assertEquals(mapOf(Numbering.FULL to 5, Numbering.NONE to 9), numbering)
    }

    @Test
    fun `album one is listed disc by disc, track by track`() {
        SampleLibraryGenerator.writeTo(dir)

        val summary = LibraryAudit.audit(dir).summary(albumNames = listOf("Album One"))

        val order = listOf("1-1 Opening", "1-2 Second", "1-3 Third", "2-1 Fourth", "2-2 Fifth", "2-3 Sixth")
        val positions = order.map { summary.indexOf(it) }
        assertTrue(positions.all { it >= 0 } && positions == positions.sorted(), summary)
    }

    @Test
    fun `partly numbered album with a duplicate track is reported`() {
        val report =
            report(
                tagged("A/one.mp3", track = 1),
                tagged("A/also one.mp3", track = 1),
                tagged("A/none.mp3"),
            )

        val album = report.albums.single()
        assertEquals(Numbering.PARTLY, album.numbering)
        assertEquals(listOf("1-1"), album.duplicates)
        assertTrue(report.summary().contains("Albums with duplicate (disc, track): 1"), report.summary())
    }

    @Test
    fun `untagged files count as titles and artists taken from the file name`() {
        val report =
            report(
                tagged("x/6LACK - Free.mp3", title = null, artist = null, album = null),
                tagged("x/intro.mp3", title = null, artist = null, album = null),
                tagged("x/Tagged.mp3"),
            )

        assertEquals(2, report.titlesFromFileName)
        assertEquals(1, report.artistsFromFileName)
        assertTrue(report.summary().contains("Artist from file name (ADR 0007): 1"), report.summary())
    }

    @Test
    fun `songs without an album are not an album`() {
        assertEquals(emptyList<AuditAlbum>(), report(tagged("x/a.mp3", album = null)).albums)
    }

    @Test
    fun `albums csv has one row per album`() {
        val csv = report(tagged("A/one.mp3", track = 1), tagged("A/two.mp3", track = 2)).albumsCsv().lines()

        assertEquals("album,album_artist,songs,numbering,duplicates", csv[0])
        assertEquals("Album,Artist,2,FULL,", csv[1])
    }
}
