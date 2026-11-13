package dev.tomasflopes.musicplayer.data.scan

import java.util.Locale

/**
 * The music folder in MediaStore terms: [volumeName] is a MediaStore volume, [relativePath] is the
 * `RELATIVE_PATH` prefix of everything inside the folder (`""` for a volume root, otherwise it ends with `/`).
 */
data class MusicRootLocation(
    val volumeName: String,
    val relativePath: String,
)

/** Maps the document id of a folder picked with the system folder picker to where MediaStore lists its files. */
object TreeUriMapper {
    private const val VOLUME_SEPARATOR = ':'
    private const val PRIMARY_DOCUMENT_VOLUME = "primary"

    /** Same value as `MediaStore.VOLUME_EXTERNAL_PRIMARY`; kept here so this file stays free of Android types. */
    private const val PRIMARY_MEDIA_VOLUME = "external_primary"
    private const val LIKE_ESCAPE = '\\'
    private const val LIKE_WILDCARDS = "%_"

    /** [documentId] looks like `primary:Music/HipHop` or `1A2B-3C4D:Music`. Any other shape gives `null`. */
    fun fromTreeDocumentId(documentId: String): MusicRootLocation? {
        val separator = documentId.indexOf(VOLUME_SEPARATOR)
        if (separator <= 0) return null
        val volumeId = documentId.substring(0, separator)
        val folder = documentId.substring(separator + 1).trim('/')
        return MusicRootLocation(
            volumeName =
                if (volumeId ==
                    PRIMARY_DOCUMENT_VOLUME
                ) {
                    PRIMARY_MEDIA_VOLUME
                } else {
                    volumeId.lowercase(Locale.ROOT)
                },
            relativePath = if (folder.isEmpty()) "" else "$folder/",
        )
    }

    /** Argument for `RELATIVE_PATH LIKE ? ESCAPE '\'`: the folder itself and everything below it. */
    fun likePattern(location: MusicRootLocation): String {
        val escaped =
            location.relativePath.fold(StringBuilder()) { pattern, char ->
                if (char == LIKE_ESCAPE || char in LIKE_WILDCARDS) pattern.append(LIKE_ESCAPE)
                pattern.append(char)
            }
        return "$escaped%"
    }
}
