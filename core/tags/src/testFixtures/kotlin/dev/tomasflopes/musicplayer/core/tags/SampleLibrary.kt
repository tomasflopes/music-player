package dev.tomasflopes.musicplayer.core.tags

import dev.tomasflopes.musicplayer.core.tags.TagVersion.ID3V1
import dev.tomasflopes.musicplayer.core.tags.TagVersion.ID3V2_2
import dev.tomasflopes.musicplayer.core.tags.TagVersion.ID3V2_3
import dev.tomasflopes.musicplayer.core.tags.TagVersion.ID3V2_4
import dev.tomasflopes.musicplayer.core.tags.TagVersion.NONE

/** One generated file: where it goes (relative to the device's shared storage) and what its tags must read as. */
data class SampleFile(
    val path: String,
    val expected: RawTags,
    val inRoot: Boolean,
)

/**
 * A small, fully generated music library (silence with real tags) that covers the cases phase 1 has to get
 * right: album order, grouping, search traps, every tag version, covers, symbols, and a file outside the root.
 */
object SampleLibrary {
    /** The folder to pick as the music folder on the emulator. */
    const val ROOT = "Music/Sample"
    private const val A = "Sample A"
    private const val B = "Sample B"
    private const val ED = "Ed"
    private const val TRACKS_PER_DISC = 3
    private const val DISCS = 2

    val files: List<SampleFile> =
        albumOne() + albumTwo() + sameName() + searchCases() + formats() + covers() + symbols() + outsideTheRoot()

    /** Two discs of three tracks; file names sort differently from the track order. */
    private fun albumOne(): List<SampleFile> {
        val songs =
            listOf(
                "c opening" to "Opening",
                "a second" to "Second",
                "b third" to "Third",
                "f fourth" to "Fourth",
                "d fifth" to "Fifth",
                "e sixth" to "Sixth",
            )
        return songs.mapIndexed { index, (fileName, title) ->
            val tags =
                tags(ID3V2_3, title, A, "Album One").copy(
                    albumArtist = A,
                    trackNumber = index % TRACKS_PER_DISC + 1,
                    trackTotal = TRACKS_PER_DISC,
                    discNumber = index / TRACKS_PER_DISC + 1,
                    discTotal = DISCS,
                )
            inRoot("Album One/$fileName.mp3", tags)
        }
    }

    /** No track numbers: the order must come from the file names, 1, 2, 10 (not 1, 10, 2). */
    private fun albumTwo(): List<SampleFile> =
        listOf("1 - x" to "X", "2 - y" to "Y", "10 - z" to "Z").map { (fileName, title) ->
            inRoot("Album Two/$fileName.mp3", tags(ID3V2_4, title, B, "Album Two").copy(albumArtist = B))
        }

    /** Two different albums that share a name; only the album artist tells them apart. */
    private fun sameName(): List<SampleFile> =
        listOf(A, B).flatMap { artist ->
            listOf("One", "Two").mapIndexed { index, title ->
                val tags = tags(ID3V2_3, "$artist $title", artist, "Same Name")
                inRoot("Same Name ($artist)/$title.mp3", tags.copy(albumArtist = artist, trackNumber = index + 1))
            }
        }

    /** The "Ed" search case: an artist called Ed, titles that merely contain "ed", and featured artists. */
    private fun searchCases(): List<SampleFile> =
        listOf(
            tags(ID3V2_3, "Ed One", ED, "Ed Songs"),
            tags(ID3V2_3, "Ed Two", ED, "Ed Songs"),
            tags(ID3V2_4, "Faded", B, "Singles"),
            tags(ID3V2_4, "Ended", B, "Singles"),
            tags(ID3V2_4, "Night feat. Ed Sample A", B, "Singles"),
            tags(ID3V2_3, "Morning ft. Ed", A, "Singles"),
            tags(ID3V2_3, "Noon feat Sample B", A, "Singles"),
            tags(ID3V2_4, "Dusk (feat. Ed) [Remix]", B, "Singles"),
            tags(ID3V2_3, "Defeat", A, "Singles"),
        ).map { inRoot("Singles/${it.artist} - ${it.title}.mp3", it) }

    private fun formats(): List<SampleFile> =
        listOf(
            inRoot("Formats/old v22.mp3", tags(ID3V2_2, "Old Format", A, "Formats").copy(trackNumber = 2)),
            inRoot("Formats/v1 only.mp3", tags(ID3V1, "Version One", B, "Formats").copy(trackNumber = 4)),
            inRoot("Formats/untagged file.mp3", UNTAGGED),
        )

    private fun covers(): List<SampleFile> =
        listOf(ID3V2_3 to "Orange One", ID3V2_4 to "Orange Two").map { (version, title) ->
            inRoot("Covers/$title.mp3", tags(version, title, A, "Covers").copy(albumArtist = A, hasCover = true))
        }

    /** Accents and symbols: shown exactly as tagged, found without typing the symbols. */
    private fun symbols(): List<SampleFile> =
        listOf(
            inRoot("Symbols/dots.mp3", tags(ID3V2_3, "Dots", "S.A.M-Ple", "Symbols")),
            inRoot("Symbols/accent.mp3", tags(ID3V2_4, "Accent", "Sam\$on Café", "Symbols")),
            inRoot("Symbols/maad.mp3", tags(ID3V2_3, "m.A.A.d sample (Remix)", B, "LOUD.")),
        )

    /** Must never show up in the app: it is outside the picked folder. */
    private fun outsideTheRoot(): List<SampleFile> =
        listOf(SampleFile("Recordings/voice note.mp3", tags(ID3V2_3, "Voice note", "Me", "Recordings"), inRoot = false))

    private val UNTAGGED get() = RawTags(null, null, null, null, null, null, null, null, false, NONE)

    private fun inRoot(
        path: String,
        tags: RawTags,
    ) = SampleFile("$ROOT/$path", tags, inRoot = true)

    private fun tags(
        version: TagVersion,
        title: String,
        artist: String,
        album: String,
    ) = RawTags(title, artist, null, album, null, null, null, null, false, version)
}
