package dev.tomasflopes.musicplayer.core.tags

import java.io.File

/** Writes [SampleLibrary] to disk: ten seconds of silence per file, with the tags each file is expected to have. */
object SampleLibraryGenerator {
    private const val SECONDS = 10
    private val V2_2_IDS = listOf("TT2", "TP1", "TP2", "TAL", "TRK", "TPA")
    private val V2_3_IDS = listOf("TIT2", "TPE1", "TPE2", "TALB", "TRCK", "TPOS")

    fun writeTo(dir: File) {
        SampleLibrary.files.forEach { sample ->
            val file = File(dir, sample.path)
            file.parentFile.mkdirs()
            file.writeBytes(bytes(sample.expected))
        }
    }

    /** Entry point of the `generateSamples` Gradle task. The only argument is the output directory. */
    @JvmStatic
    fun main(args: Array<String>) {
        writeTo(File(args.single()))
    }

    private fun bytes(tags: RawTags): ByteArray {
        val silence = SilentMp3.frames(SECONDS)
        return when (tags.version) {
            TagVersion.ID3V2_2 -> id3v22 { frames(tags, V2_2_IDS, TextEncoding.LATIN1, silence) }
            TagVersion.ID3V2_3 -> id3v23 { frames(tags, V2_3_IDS, TextEncoding.UTF16, silence) }
            TagVersion.ID3V2_4 -> id3v24 { frames(tags, V2_3_IDS, TextEncoding.UTF8, silence) }
            TagVersion.ID3V1 ->
                silence + id3v1(tags.title.orEmpty(), tags.artist.orEmpty(), tags.album.orEmpty(), tags.trackNumber)
            TagVersion.NONE -> silence
        }
    }

    private fun Id3TagWriter.frames(
        tags: RawTags,
        ids: List<String>,
        encoding: TextEncoding,
        silence: ByteArray,
    ) {
        val values =
            listOf(
                tags.title,
                tags.artist,
                tags.albumArtist,
                tags.album,
                numberPair(tags.trackNumber, tags.trackTotal),
                numberPair(tags.discNumber, tags.discTotal),
            )
        ids.zip(values).forEach { (id, value) -> if (value != null) text(id, value, encoding) }
        if (tags.hasCover) picture(OrangePng.bytes())
        audio(silence)
    }

    private fun numberPair(
        number: Int?,
        total: Int?,
    ): String? =
        when {
            number == null -> null
            total == null -> "$number"
            else -> "$number/$total"
        }
}
