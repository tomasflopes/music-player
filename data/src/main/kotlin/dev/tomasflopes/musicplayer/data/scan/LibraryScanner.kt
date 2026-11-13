package dev.tomasflopes.musicplayer.data.scan

import androidx.room.withTransaction
import dev.tomasflopes.musicplayer.core.model.SongId
import dev.tomasflopes.musicplayer.core.model.config.LibraryConfig
import dev.tomasflopes.musicplayer.core.model.ports.MusicRoot
import dev.tomasflopes.musicplayer.core.model.ports.ScanPhase
import dev.tomasflopes.musicplayer.core.model.ports.ScanProgress
import dev.tomasflopes.musicplayer.core.search.FeaturedArtistParser
import dev.tomasflopes.musicplayer.core.tags.TagReader
import dev.tomasflopes.musicplayer.core.tags.TagSource
import dev.tomasflopes.musicplayer.data.db.MusicDatabase
import dev.tomasflopes.musicplayer.data.db.entity.AlbumEntity
import dev.tomasflopes.musicplayer.data.db.entity.ArtistEntity
import dev.tomasflopes.musicplayer.data.db.entity.FolderEntity
import dev.tomasflopes.musicplayer.data.db.entity.SongEntity
import dev.tomasflopes.musicplayer.data.db.entity.SongFeatureEntity
import dev.tomasflopes.musicplayer.data.scan.LibraryAssembler.sortKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * One scan: list the files, plan against what is known, read the tags of new and changed files, write in
 * batches. Songs are never deleted and `date_added` is never overwritten (plan C4).
 */
class LibraryScanner(
    private val lister: SongFileLister,
    private val tagSources: (MediaFile) -> TagSource,
    private val tagReader: TagReader,
    private val db: MusicDatabase,
    private val config: LibraryConfig,
) {
    suspend fun scan(
        root: MusicRoot,
        onProgress: (ScanProgress) -> Unit,
    ) {
        onProgress(ScanProgress(ScanPhase.LISTING, 0, 0))
        scanFiles(root, listFiles(root), onProgress)
    }

    suspend fun listFiles(root: MusicRoot): List<MediaFile> = lister.list(root)

    /** The scan from the file list on; split out so tests can take a file away. */
    suspend fun scanFiles(
        root: MusicRoot,
        found: List<MediaFile>,
        onProgress: (ScanProgress) -> Unit,
    ) {
        val plan = ScanPlanner.plan(found, db.songScanDao().knownSongs().map { it.toKnownSong() })
        val toRead = plan.insert.map { null to it } + plan.rereadTags
        var done = 0
        onProgress(ScanProgress(ScanPhase.READING_TAGS, 0, toRead.size))
        val assembled =
            withContext(Dispatchers.IO) {
                toRead.map { (id, file) ->
                    val song = LibraryAssembler.assemble(file, tagReader.read(tagSources(file)))
                    onProgress(ScanProgress(ScanPhase.READING_TAGS, ++done, toRead.size))
                    id to song
                }
            }
        val writes = Writes(db, root)
        var written = 0
        onProgress(ScanProgress(ScanPhase.INDEXING, 0, assembled.size))
        assembled.chunked(config.scanBatchSize).forEach { batch ->
            db.withTransaction { batch.forEach { (id, song) -> writes.write(id, song) } }
            written += batch.size
            onProgress(ScanProgress(ScanPhase.INDEXING, written, assembled.size))
        }
        db.withTransaction {
            plan.move.forEach { (id, file) ->
                val folder = file.path.substringBeforeLast('/', "")
                db.songScanDao().move(id.value, file.path, file.path.substringAfterLast('/'), folder, file.mediaStoreId)
            }
            plan.refreshMediaStoreId.forEach { (id, file) ->
                db.songScanDao().refreshMediaStoreId(id.value, file.mediaStoreId)
            }
            plan.revive.forEach { (id, _) -> db.songScanDao().setInLibrary(id.value, true) }
            plan.retire.forEach { db.songScanDao().setInLibrary(it.value, false) }
            writes.foldersFor(plan.move.map { it.second } + plan.revive.map { it.second })
            writeFeaturedArtists()
        }
        onProgress(ScanProgress(ScanPhase.DONE, found.size, found.size))
    }

    /** Recomputes song_feature from the current titles and known main artists (ADR 0007, §7.11). */
    private suspend fun writeFeaturedArtists() {
        val artists = db.artistDao().allArtistNameId().associate { it.name to it.id }
        val songs = db.songDao().allSongTitles()
        db.withTransaction {
            db.songFeatureDao().deleteAll()
            for ((id, title) in songs) {
                for (name in FeaturedArtistParser.parse(title, artists.keys.sortedByDescending { it.length })) {
                    val artistId = artists[name]
                    db.songFeatureDao().insert(
                        SongFeatureEntity(
                            songId = id,
                            artistId = artistId,
                            rawName = if (artistId == null) name else null,
                        ),
                    )
                }
            }
        }
    }

    /** Resolves artists, albums and folders once per scan and writes songs. Runs inside transactions. */
    private class Writes(
        private val db: MusicDatabase,
        private val root: MusicRoot,
    ) {
        private val artists = mutableMapOf<String, Long>()
        private val albums = mutableMapOf<Pair<String, String>, Long>()
        private val folders = mutableSetOf<String>()

        suspend fun write(
            id: SongId?,
            song: AssembledSong,
        ) {
            val artistId = artist(song.artistName)
            val albumId = song.albumKey?.let { album(it.title, it.albumArtist) }
            foldersFor(listOf(song.file))
            val entity =
                SongEntity(
                    id = id?.value ?: 0,
                    mediaStoreId = song.file.mediaStoreId,
                    volumeName = song.file.volumeName,
                    path = song.file.path,
                    fileName = song.file.path.substringAfterLast('/'),
                    folderPath = song.folderPath,
                    title = song.title,
                    sortTitle = song.sortTitle,
                    artistId = artistId,
                    albumId = albumId,
                    albumArtist = song.albumKey?.albumArtist,
                    trackNumber = song.trackNumber,
                    discNumber = song.discNumber,
                    durationMs = song.file.durationMs,
                    fileSize = song.file.sizeBytes,
                    fileModified = song.file.modifiedEpochSec,
                    dateAdded = song.file.dateAddedEpochSec,
                    hasCover = song.hasCover,
                    tagVersion = song.tagVersion.name,
                    inLibrary = true,
                )
            if (id == null) db.songScanDao().insert(entity) else update(entity)
        }

        private suspend fun update(song: SongEntity) =
            with(song) {
                db.songScanDao().updateTags(
                    id = id,
                    mediaStoreId = mediaStoreId,
                    path = path,
                    fileName = fileName,
                    folderPath = folderPath,
                    title = title,
                    sortTitle = sortTitle,
                    artistId = artistId,
                    albumId = albumId,
                    albumArtist = albumArtist,
                    trackNumber = trackNumber,
                    discNumber = discNumber,
                    durationMs = durationMs,
                    fileSize = fileSize,
                    fileModified = fileModified,
                    hasCover = hasCover,
                    tagVersion = tagVersion,
                )
            }

        suspend fun foldersFor(files: List<MediaFile>) {
            files.forEach { file ->
                val chain = LibraryAssembler.folderChain(file.path.substringBeforeLast('/', ""), root.relativePath)
                chain.filter { folders.add(it.path) && db.folderDao().byPath(it.path) == null }.forEach { folder ->
                    db.folderDao().insert(FolderEntity(folder.path, folder.name, folder.parentPath))
                }
            }
        }

        private suspend fun artist(name: String): Long =
            artists.getOrPut(name) {
                db.artistDao().byName(name)?.id
                    ?: db.artistDao().insert(ArtistEntity(name = name, sortName = sortKey(name)))
            }

        private suspend fun newAlbum(
            title: String,
            albumArtist: String,
        ): Long {
            val album = AlbumEntity(title = title, albumArtist = albumArtist, sortTitle = sortKey(title))
            return db.albumDao().insert(album)
        }

        private suspend fun album(
            title: String,
            albumArtist: String,
        ): Long =
            albums.getOrPut(title to albumArtist) {
                db.albumDao().byKey(title, albumArtist)?.id
                    ?: newAlbum(title, albumArtist)
            }
    }
}
