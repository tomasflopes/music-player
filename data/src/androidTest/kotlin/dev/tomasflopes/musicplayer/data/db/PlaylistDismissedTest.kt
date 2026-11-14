package dev.tomasflopes.musicplayer.data.db

import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.tomasflopes.musicplayer.core.model.SongId
import dev.tomasflopes.musicplayer.core.model.playlists.PlaylistId
import dev.tomasflopes.musicplayer.data.db.entity.PlaylistEntity
import dev.tomasflopes.musicplayer.data.repo.RoomPlaylistDismissals
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlaylistDismissedTest {
    private lateinit var db: MusicDatabase
    private lateinit var dismissals: RoomPlaylistDismissals

    @Before
    fun open() {
        db = inMemoryDatabase()
        dismissals = RoomPlaylistDismissals(db)
    }

    @After
    fun close() {
        db.close()
    }

    private suspend fun playlist() =
        PlaylistId(db.playlistDao().insert(PlaylistEntity(name = "Gym", createdAt = 1, updatedAt = 1, lastUsedAt = 1)))

    @Test
    fun aDismissedSongIsReportedForThatPlaylistOnly() =
        runTest {
            val gym = playlist()
            val other = playlist()

            dismissals.dismiss(gym, SongId(5), 1)

            assertEquals(setOf(SongId(5)), dismissals.observe(gym).first())
            assertEquals(emptySet<SongId>(), dismissals.observe(other).first())
        }

    @Test
    fun dismissingTwiceKeepsOneRow() =
        runTest {
            val gym = playlist()

            dismissals.dismiss(gym, SongId(5), 1)
            dismissals.dismiss(gym, SongId(5), 2)

            assertEquals(setOf(SongId(5)), dismissals.observe(gym).first())
        }

    @Test
    fun aPlaylistThatDoesNotExistIgnoresTheDismissal() =
        runTest {
            dismissals.dismiss(PlaylistId(99), SongId(5), 1)

            assertEquals(emptySet<SongId>(), dismissals.observe(PlaylistId(99)).first())
        }

    @Test
    fun deletingThePlaylistTakesItsDismissalsWithIt() =
        runTest {
            val gym = playlist()
            dismissals.dismiss(gym, SongId(5), 1)

            db.playlistDao().delete(gym.value)

            assertEquals(emptySet<SongId>(), dismissals.observe(gym).first())
        }
}
