package dev.tomasflopes.musicplayer.core.taste.lists

import app.cash.turbine.test
import dev.tomasflopes.musicplayer.core.model.AlbumId
import dev.tomasflopes.musicplayer.core.model.ArtistId
import dev.tomasflopes.musicplayer.core.model.Song
import dev.tomasflopes.musicplayer.core.model.SongId
import dev.tomasflopes.musicplayer.core.model.SongRef
import dev.tomasflopes.musicplayer.core.model.ports.LibraryChanges
import dev.tomasflopes.musicplayer.core.model.ports.SongReader
import dev.tomasflopes.musicplayer.core.model.ref
import dev.tomasflopes.musicplayer.core.taste.config.TasteConfig
import dev.tomasflopes.musicplayer.core.taste.score.SongScore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** A library that changes while the list is being collected, like a rescan that retires or revives songs. */
internal class MutableLibrary(
    initial: List<Song>,
) : SongReader,
    LibraryChanges {
    private val current = MutableStateFlow(initial)

    fun retire(id: Long) {
        current.value = current.value.filter { it.id != SongId(id) }
    }

    fun restore(song: Song) {
        current.value = current.value + song
    }

    override fun observe(): Flow<Unit> = current.map { }

    override fun observeSongs(): Flow<List<Song>> = current

    override fun observeAddedSince(epochSec: Long): Flow<List<Song>> = emptyFlow()

    override fun observeAlbumSongs(id: AlbumId): Flow<List<Song>> = emptyFlow()

    override fun observeArtistSongs(id: ArtistId): Flow<List<Song>> = emptyFlow()

    override fun observeFeaturedOn(id: ArtistId): Flow<List<Song>> = emptyFlow()

    override fun observeFolderSongs(folderPath: String): Flow<List<Song>> = emptyFlow()

    override suspend fun songsByIds(ids: List<SongId>): List<Song> {
        val byId = current.value.associateBy { it.id }
        return ids.mapNotNull(byId::get)
    }

    override suspend fun allSongRefs(): List<SongRef> = current.value.map { it.ref }
}

/** Audit 2026-11-14, S3: the Top 100 follows the library, not only the listening log. */
class TopHundredLibraryAuditTest {
    private val board = FakeScoreBoard(listOf(SongScore(SongId(1), 2.0, 1), SongScore(SongId(2), 1.0, 1)))

    private fun provider(library: MutableLibrary) = TopHundredProvider(board, library, library, TasteConfig())

    @Test
    fun `S3 a song retired by a rescan leaves the list`() =
        runTest {
            val library = MutableLibrary(listOf(song(1, 0), song(2, 0)))

            provider(library).songs().test {
                assertEquals(listOf(1L, 2L), awaitItem().map { it.id.value })

                library.retire(2)

                assertEquals(listOf(1L), awaitItem().map { it.id.value })
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `S3 a song that returns to the library comes back into the list`() =
        runTest {
            val library = MutableLibrary(listOf(song(1, 0)))

            provider(library).songs().test {
                assertEquals(listOf(1L), awaitItem().map { it.id.value })

                library.restore(song(2, 0))

                assertEquals(listOf(1L, 2L), awaitItem().map { it.id.value })
                cancelAndIgnoreRemainingEvents()
            }
        }
}
