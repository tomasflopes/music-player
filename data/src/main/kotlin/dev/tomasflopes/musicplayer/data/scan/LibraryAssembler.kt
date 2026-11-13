package dev.tomasflopes.musicplayer.data.scan

import dev.tomasflopes.musicplayer.core.model.AlbumKey
import dev.tomasflopes.musicplayer.core.model.DisplayFallbacks
import dev.tomasflopes.musicplayer.core.model.Folder
import dev.tomasflopes.musicplayer.core.tags.RawTags
import dev.tomasflopes.musicplayer.core.tags.TagReadResult
import dev.tomasflopes.musicplayer.core.tags.TagVersion
import java.text.Normalizer
import java.util.Locale

/** A file plus what the library will store for it: tags as read, with the display fallbacks already applied. */
data class AssembledSong(
    val file: MediaFile,
    val title: String,
    val artistName: String,
    val albumKey: AlbumKey?,
    val trackNumber: Int?,
    val discNumber: Int?,
    val hasCover: Boolean,
    val tagVersion: TagVersion,
    val folderPath: String,
    val sortTitle: String,
)

/** Turns a file and its tag read into what the database stores. Pure Kotlin. */
object LibraryAssembler {
    private val ACCENTS = Regex("\\p{Mn}+")
    private val LEADING_ARTICLE = Regex("^(the|a|an) ", RegexOption.IGNORE_CASE)
    private val NO_TAGS = RawTags(null, null, null, null, null, null, null, null, false, TagVersion.NONE)

    fun assemble(
        file: MediaFile,
        tags: TagReadResult,
    ): AssembledSong {
        val raw = (tags as? TagReadResult.Success)?.tags ?: NO_TAGS
        val fileName = file.path.substringAfterLast('/')
        val title = DisplayFallbacks.title(raw.title, fileName)
        val artist = DisplayFallbacks.artist(raw.artist, fileName)
        return AssembledSong(
            file = file,
            title = title,
            artistName = artist,
            albumKey = AlbumKey.of(raw.album, raw.albumArtist, artist),
            trackNumber = raw.trackNumber,
            discNumber = raw.discNumber,
            hasCover = raw.hasCover,
            tagVersion = raw.version,
            folderPath = file.path.substringBeforeLast('/', ""),
            sortTitle = sortKey(title),
        )
    }

    /** Lower case, accents removed, a leading article dropped (unless that is the whole title). */
    fun sortKey(text: String): String {
        val plain =
            Normalizer
                .normalize(text, Normalizer.Form.NFD)
                .replace(ACCENTS, "")
                .lowercase(Locale.ROOT)
                .trim()
        return plain.replace(LEADING_ARTICLE, "").ifEmpty { plain }
    }

    /** The part of [folderPath] below [root]; `null` when it is the root itself or lies outside it. */
    private fun relativeToRoot(
        folderPath: String,
        root: String,
    ): String? =
        when {
            folderPath == root || folderPath.isEmpty() -> null
            root.isEmpty() -> folderPath
            folderPath.startsWith("$root/") -> folderPath.removePrefix("$root/")
            else -> null
        }

    /**
     * Every folder from just below the music root down to [folderPath], each with its parent. The root
     * itself has no row: its children have `parentPath = null`. [rootPath] is `""` or ends with `/`.
     */
    fun folderChain(
        folderPath: String,
        rootPath: String,
    ): List<Folder> {
        val root = rootPath.trimEnd('/')
        val below = relativeToRoot(folderPath, root) ?: return emptyList()
        val names = below.split('/')
        return names.indices.map { index ->
            val relative = names.take(index + 1).joinToString("/")
            val path = if (root.isEmpty()) relative else "$root/$relative"
            val parent = if (index == 0) null else path.substringBeforeLast('/')
            Folder(path = path, name = names[index], parentPath = parent, songCount = 0)
        }
    }
}
