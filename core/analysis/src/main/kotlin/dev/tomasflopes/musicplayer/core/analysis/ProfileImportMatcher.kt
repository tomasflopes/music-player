package dev.tomasflopes.musicplayer.core.analysis

import java.text.Normalizer

/** A library song as the matcher needs it: its MediaStore path (relative to the volume), size and retired state. */
data class ImportTarget(
    val songId: Long,
    val path: String,
    val fileSize: Long,
    val inLibrary: Boolean,
)

/**
 * Where an export line landed: the song that receives the profile, and whether the song counts in the summary.
 * A retired song (ADR 0021) is matched so its profile is kept, but is not counted (ADR 0057).
 */
data class ProfileMatch(
    val songId: Long,
    val counted: Boolean,
)

/**
 * Matches export lines to library songs on the NFC-normalised path relative to the music root plus the exact file
 * size (ADR 0057). Never MediaStore ids. Pure: no Android types.
 */
class ProfileImportMatcher(
    targets: Sequence<ImportTarget>,
    musicRoot: String,
) {
    private val index: Map<String, ImportTarget>

    init {
        val root = rootPrefix(musicRoot)
        val built = HashMap<String, ImportTarget>()
        for (target in targets) {
            val relative = relativeTo(root, target.path) ?: continue
            val key = key(relative, target.fileSize)
            val existing = built[key]
            // Two rows with the same path and size: prefer the one in the library.
            if (existing == null || (!existing.inLibrary && target.inLibrary)) built[key] = target
        }
        index = built
    }

    /** The song for an export line with this relative [path] and [size], or null when nothing matches. */
    fun match(
        path: String,
        size: Long,
    ): ProfileMatch? {
        val target = index[key(nfc(path), size)] ?: return null
        return ProfileMatch(target.songId, target.inLibrary)
    }

    companion object {
        /** NFC form: macOS stores names decomposed (NFD), Android and the export use composed (NFC). */
        fun nfc(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFC)

        /** The root as a prefix that ends in a slash, NFC-normalised, so `Music` and `Music/` match alike. */
        private fun rootPrefix(musicRoot: String): String = nfc(musicRoot.trimEnd('/')) + "/"

        /** The NFC relative path of [path] under [root], or null when it is outside the root. */
        private fun relativeTo(
            root: String,
            path: String,
        ): String? {
            val composed = nfc(path)
            return if (composed.startsWith(root)) composed.substring(root.length) else null
        }

        private fun key(
            relativeNfc: String,
            size: Long,
        ): String = "$size|$relativeNfc"
    }
}
