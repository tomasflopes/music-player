package dev.tomasflopes.musicplayer.data.scan

import dev.tomasflopes.musicplayer.core.model.SongId

/** One MP3 as MediaStore lists it. */
data class MediaFile(
    val mediaStoreId: Long,
    val volumeName: String,
    val path: String,
    val sizeBytes: Long,
    val modifiedEpochSec: Long,
    val dateAddedEpochSec: Long,
    val durationMs: Long,
    val contentUri: String,
)

/** What the database already knows about a song, enough to decide what the scan must do with it. */
data class KnownSong(
    val id: SongId,
    val mediaStoreId: Long,
    val volumeName: String,
    val path: String,
    val sizeBytes: Long,
    val modifiedEpochSec: Long,
    val inLibrary: Boolean,
)

/**
 * The work one scan has to do. Songs are never deleted (plan C4): a missing file is retired, a file that
 * comes back is revived, a file that moved keeps its id. A song can appear in `rereadTags` together with
 * `move` or `revive` when its content changed as well.
 */
data class ScanPlan(
    val insert: List<MediaFile>,
    val rereadTags: List<Pair<SongId, MediaFile>>,
    val move: List<Pair<SongId, MediaFile>>,
    val revive: List<Pair<SongId, MediaFile>>,
    /** Same path, new MediaStore id (the index was rebuilt): remember the new id, nothing else changes. */
    val refreshMediaStoreId: List<Pair<SongId, MediaFile>>,
    val retire: List<SongId>,
    val unchanged: Int,
)

/** Matches the files MediaStore lists against the songs the database knows. Pure; the scanner applies the plan. */
object ScanPlanner {
    fun plan(
        found: List<MediaFile>,
        known: List<KnownSong>,
    ): ScanPlan {
        val matcher = KnownSongMatcher(known)
        val steps = found.map { file -> matcher.match(file)?.let { Step(it, file) } ?: Step(null, file) }
        val matched = steps.mapNotNull { it.known }.map { it.id }.toSet()
        return ScanPlan(
            insert = steps.filter { it.known == null }.map { it.file },
            rereadTags = steps.filter { it.contentChanged }.map { it.pair() },
            move = steps.filter { it.moved }.map { it.pair() },
            revive = steps.filter { it.revived }.map { it.pair() },
            refreshMediaStoreId = steps.filter { it.reindexed }.map { it.pair() },
            retire = known.filter { it.inLibrary && it.id !in matched }.map { it.id },
            unchanged = steps.count { it.unchanged },
        )
    }

    /** One found file and the known song it was matched to, if any. */
    private class Step(
        val known: KnownSong?,
        val file: MediaFile,
    ) {
        private val samePath = known != null && known.path == file.path && known.volumeName == file.volumeName
        val moved = known != null && !samePath
        val revived = known != null && !known.inLibrary
        val reindexed = known != null && samePath && known.mediaStoreId != file.mediaStoreId
        val contentChanged =
            known != null && (known.sizeBytes != file.sizeBytes || known.modifiedEpochSec != file.modifiedEpochSec)
        val unchanged = known != null && samePath && known.inLibrary && !contentChanged

        fun pair() = checkNotNull(known).id to file
    }

    /** Finds a known song by volume + path first, then by volume + MediaStore id; each song matches once. */
    private class KnownSongMatcher(
        known: List<KnownSong>,
    ) {
        private val byPath = known.associateBy { it.volumeName to it.path }
        private val byMediaStoreId = known.filter { it.inLibrary }.groupBy { it.volumeName to it.mediaStoreId }
        private val taken = mutableSetOf<SongId>()

        fun match(file: MediaFile): KnownSong? {
            val candidate =
                byPath[file.volumeName to file.path]
                    ?: byMediaStoreId[file.volumeName to file.mediaStoreId]?.firstOrNull { it.id !in taken }
            return candidate?.takeIf { taken.add(it.id) }
        }
    }
}
