package dev.tomasflopes.musicplayer.data.db

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MusicDatabaseMigrationTest {
    private val db = "migration-test.db"

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), MusicDatabase::class.java)

    @Test
    fun createsV1AndOpensWithRealBuilder() {
        helper.createDatabase(db, 1).close()

        Room
            .databaseBuilder(ApplicationProvider.getApplicationContext(), MusicDatabase::class.java, db)
            .addMigrations(*Migrations.ALL)
            .build()
            .apply { openHelper.writableDatabase.close() }
            .close()
    }

    @Test
    fun everyGuidelineIndexExists() {
        val database = helper.createDatabase(db, 1)

        fun indexedColumns(table: String): Set<String> {
            val names = mutableListOf<String>()
            database.query("PRAGMA index_list(`$table`)").use { while (it.moveToNext()) names += it.getString(1) }
            return names
                .flatMap { index ->
                    val columns = mutableListOf<String>()
                    database.query("PRAGMA index_info(`$index`)").use { cursor ->
                        while (cursor.moveToNext()) columns += cursor.getString(2)
                    }
                    columns.take(1)
                }.toSet()
        }

        val songIndexes = listOf("artist_id", "album_id", "folder_path", "date_added", "sort_title")
        assertTrue(indexedColumns("song").containsAll(songIndexes))
        assertTrue(indexedColumns("play_event").containsAll(listOf("song_id", "started_at")))
        assertEquals(setOf<String>(), foreignKeys(database, "play_event"))
        database.close()
    }

    private fun foreignKeys(
        database: androidx.sqlite.db.SupportSQLiteDatabase,
        table: String,
    ): Set<String> {
        val tables = mutableSetOf<String>()
        database.query("PRAGMA foreign_key_list(`$table`)").use { while (it.moveToNext()) tables += it.getString(2) }
        return tables
    }
}
