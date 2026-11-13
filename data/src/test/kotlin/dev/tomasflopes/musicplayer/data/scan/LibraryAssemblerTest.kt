package dev.tomasflopes.musicplayer.data.scan

import dev.tomasflopes.musicplayer.core.model.AlbumKey
import dev.tomasflopes.musicplayer.core.model.DisplayFallbacks
import dev.tomasflopes.musicplayer.core.model.Folder
import dev.tomasflopes.musicplayer.core.tags.RawTags
import dev.tomasflopes.musicplayer.core.tags.TagReadResult
import dev.tomasflopes.musicplayer.core.tags.TagVersion
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class LibraryAssemblerTest {
    private fun file(path: String) =
        MediaFile(
            mediaStoreId = 1,
            volumeName = "external_primary",
            path = path,
            sizeBytes = 100,
            modifiedEpochSec = 10,
            dateAddedEpochSec = 1,
            durationMs = 1_000,
            contentUri = "content://1",
        )

    private fun tags(
        title: String? = null,
        artist: String? = null,
        albumArtist: String? = null,
        album: String? = null,
        track: Int? = null,
        disc: Int? = null,
        cover: Boolean = false,
    ) = TagReadResult.Success(
        RawTags(title, artist, albumArtist, album, track, null, disc, null, cover, TagVersion.ID3V2_3),
        emptyList(),
    )

    @Test
    fun `tagged file keeps every tag exactly`() {
        val tags = tags("HUMBLE.", "Kendrick Lamar", "KL", "DAMN.", track = 8, disc = 1, cover = true)

        val song = LibraryAssembler.assemble(file("Music/HH/x.mp3"), tags)

        assertEquals("HUMBLE.", song.title)
        assertEquals("Kendrick Lamar", song.artistName)
        assertEquals(AlbumKey("DAMN.", "KL"), song.albumKey)
        assertEquals(8 to 1, song.trackNumber to song.discNumber)
        assertEquals(true, song.hasCover)
        assertEquals(TagVersion.ID3V2_3, song.tagVersion)
        assertEquals("Music/HH", song.folderPath)
    }

    @Test
    fun `missing tags fall back to the file name, with artist - title split`() {
        val song = LibraryAssembler.assemble(file("Music/HH/6LACK - Free.mp3"), tags())

        assertEquals("Free", song.title)
        assertEquals("6LACK", song.artistName)
        assertNull(song.albumKey)
    }

    @Test
    fun `tag failure gives file-name title, unknown artist and no album`() {
        val song = LibraryAssembler.assemble(file("Music/HH/intro.mp3"), TagReadResult.Failure("too short"))

        assertEquals("intro", song.title)
        assertEquals(DisplayFallbacks.UNKNOWN_ARTIST, song.artistName)
        assertNull(song.albumKey)
        assertEquals(TagVersion.NONE, song.tagVersion)
    }

    @Test
    fun `sort title ignores case, accents and a leading article`() {
        assertEquals("edge", LibraryAssembler.assemble(file("Music/x.mp3"), tags("Édge")).sortTitle)
        assertEquals("weeknd", LibraryAssembler.assemble(file("Music/x.mp3"), tags("The Weeknd")).sortTitle)
        assertEquals("the", LibraryAssembler.assemble(file("Music/x.mp3"), tags("The")).sortTitle)
    }

    @Test
    fun `folder chain lists every folder between the root and the file, with parents`() {
        val chain = LibraryAssembler.folderChain("Music/HipHop/Ed/Album", rootPath = "Music/HipHop/")

        assertEquals(
            listOf(
                Folder("Music/HipHop/Ed", "Ed", parentPath = null, songCount = 0),
                Folder("Music/HipHop/Ed/Album", "Album", parentPath = "Music/HipHop/Ed", songCount = 0),
            ),
            chain,
        )
    }

    @Test
    fun `file directly in the root has no folder chain`() {
        assertEquals(emptyList<Folder>(), LibraryAssembler.folderChain("Music", rootPath = "Music/"))
    }

    @Test
    fun `volume root works as a root path`() {
        val chain = LibraryAssembler.folderChain("Music/HH", rootPath = "")

        assertEquals(listOf("Music", "Music/HH"), chain.map { it.path })
        assertEquals(listOf(null, "Music"), chain.map { it.parentPath })
    }
}
