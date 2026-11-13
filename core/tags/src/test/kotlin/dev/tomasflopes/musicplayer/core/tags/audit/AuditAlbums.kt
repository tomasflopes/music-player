package dev.tomasflopes.musicplayer.core.tags.audit

import dev.tomasflopes.musicplayer.core.model.AlbumId
import dev.tomasflopes.musicplayer.core.model.AlbumKey
import dev.tomasflopes.musicplayer.core.model.AlbumOrder
import dev.tomasflopes.musicplayer.core.model.ArtistId
import dev.tomasflopes.musicplayer.core.model.DisplayFallbacks
import dev.tomasflopes.musicplayer.core.model.Song
import dev.tomasflopes.musicplayer.core.model.SongId
import dev.tomasflopes.musicplayer.core.tags.RawTags

internal enum class Numbering { FULL, PARTLY, NONE }

/** One album as the app will group it, its songs already in [AlbumOrder]. */
internal data class AuditAlbum(
    val key: AlbumKey,
    val songs: List<Song>,
) {
    val numbering: Numbering
        get() {
            val numbered = songs.count { it.trackNumber != null }
            return when (numbered) {
                songs.size -> Numbering.FULL
                0 -> Numbering.NONE
                else -> Numbering.PARTLY
            }
        }

    /** `disc-track` of every (disc, track) pair used by more than one song. */
    val duplicates: List<String>
        get() =
            songs
                .filter { it.trackNumber != null }
                .groupingBy { position(it) }
                .eachCount()
                .filterValues { it > 1 }
                .keys
                .toList()

    fun orderLines(): List<String> = songs.map { "${position(it)} ${it.title}" }

    private fun position(song: Song) = "${song.discNumber ?: 1}-${song.trackNumber ?: "?"}"
}

/** Applies the app's own rules (DisplayFallbacks, AlbumKey, AlbumOrder) to what the reader found. */
internal object AuditAlbums {
    fun group(read: List<Pair<String, RawTags>>): List<AuditAlbum> =
        read
            .mapNotNull { (path, tags) -> song(path, tags)?.let { it to tags } }
            .groupBy(
                { (song, tags) -> checkNotNull(AlbumKey.of(tags.album, tags.albumArtist, song.artistName)) },
                { (song, _) -> song },
            ).map { (key, songs) -> AuditAlbum(key, songs.sortedWith(AlbumOrder)) }
            .sortedWith(compareBy({ it.key.title }, { it.key.albumArtist }))

    /** `null` when the song has no album. */
    private fun song(
        path: String,
        tags: RawTags,
    ): Song? {
        val fileName = path.substringAfterLast('/')
        val artist = DisplayFallbacks.artist(tags.artist, fileName)
        if (AlbumKey.of(tags.album, tags.albumArtist, artist) == null) return null
        return Song(
            id = SongId(path.hashCode().toLong()),
            title = DisplayFallbacks.title(tags.title, fileName),
            artistId = ArtistId(0),
            artistName = artist,
            albumId = AlbumId(0),
            albumTitle = tags.album,
            albumArtist = tags.albumArtist,
            trackNumber = tags.trackNumber,
            discNumber = tags.discNumber,
            durationMs = 0,
            volumeName = "",
            path = path,
            fileName = fileName,
            folderPath = path.substringBeforeLast('/', ""),
            dateAddedEpochSec = 0,
            contentUri = "",
            hasCover = tags.hasCover,
        )
    }
}

private const val CSV_SPECIAL = ",\"\r\n"

/** One CSV line; fields with a comma, quote or line break are quoted. */
internal fun csvLine(values: List<Any?>): String =
    values.joinToString(",") { value ->
        val text = value?.toString().orEmpty()
        if (text.any { it in CSV_SPECIAL }) "\"${text.replace("\"", "\"\"")}\"" else text
    }
