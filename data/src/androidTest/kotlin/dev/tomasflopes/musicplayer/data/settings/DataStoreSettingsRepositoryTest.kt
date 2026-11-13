package dev.tomasflopes.musicplayer.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.tomasflopes.musicplayer.core.model.config.LibraryConfig
import dev.tomasflopes.musicplayer.core.model.ports.MusicRoot
import dev.tomasflopes.musicplayer.core.model.ports.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class DataStoreSettingsRepositoryTest {
    private val file =
        File(ApplicationProvider.getApplicationContext<Context>().cacheDir, "settings-test.preferences_pb")
    private var scope: CoroutineScope? = null

    /**
     * A fresh instance over the same file, the way a new app process would see it. DataStore allows one live
     * instance per file, so the previous one is shut down first.
     */
    private fun newInstance(config: LibraryConfig = LibraryConfig()): SettingsRepository {
        scope?.cancel()
        val fresh = CoroutineScope(Dispatchers.IO + SupervisorJob()).also { scope = it }
        val store: DataStore<Preferences> = PreferenceDataStoreFactory.create(scope = fresh) { file }
        return DataStoreSettingsRepository(store, config)
    }

    @Before
    fun clean() {
        file.delete()
    }

    @After
    fun stop() {
        scope?.cancel()
        file.delete()
    }

    @Test
    fun defaultDaysIsTwenty() =
        runTest {
            assertEquals(20, newInstance().recentlyAddedDays.first())
        }

    @Test
    fun defaultDaysComesFromConfig() =
        runTest {
            assertEquals(30, newInstance(LibraryConfig(recentlyAddedDefaultDays = 30)).recentlyAddedDays.first())
        }

    @Test
    fun daysSurviveANewInstance() =
        runTest {
            newInstance().setRecentlyAddedDays(15)

            assertEquals(15, newInstance().recentlyAddedDays.first())
        }

    @Test
    fun daysOutsideTheRangeAreCoerced() =
        runTest {
            val settings = newInstance()

            settings.setRecentlyAddedDays(0)
            assertEquals(1, settings.recentlyAddedDays.first())

            settings.setRecentlyAddedDays(10_000)
            assertEquals(3650, settings.recentlyAddedDays.first())
        }

    @Test
    fun musicRootIsNullUntilSetThenRoundTrips() =
        runTest {
            assertNull(newInstance().musicRoot.first())
            val treeUri = "content://com.android.externalstorage.documents/tree/primary%3AMusic"
            val root = MusicRoot(treeUri, "external_primary", "Music/")

            newInstance().setMusicRoot(root)

            assertEquals(root, newInstance().musicRoot.first())
        }
}
