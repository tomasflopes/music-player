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

    @Test
    fun migration1To2KeepsTheLibraryAndAddsTheArtistImageTables() {
        helper.createDatabase(db, 1).use { v1 ->
            v1.execSQL("INSERT INTO artist (id, name, sort_name) VALUES (7, 'Rui Veloso', 'rui veloso')")
        }

        helper.runMigrationsAndValidate(db, 2, true, Migrations.MIGRATION_1_2).use { v2 ->
            v2.query("SELECT name FROM artist WHERE id = 7").use {
                assertTrue(it.moveToFirst())
                assertEquals("Rui Veloso", it.getString(0))
            }
            v2.execSQL(
                "INSERT INTO artist_image_override (artist_id, kind, value, updated_at) " +
                    "VALUES (7, 'generated', NULL, 1)",
            )
            v2.execSQL("INSERT INTO artist_image_lookup (artist_id, picture_url, checked_at) VALUES (7, NULL, 1)")
            v2.query("SELECT COUNT(*) FROM artist_image_override").use {
                assertTrue(it.moveToFirst())
                assertEquals(1, it.getInt(0))
            }
        }
    }

    @Test
    fun migration2To3KeepsTheLibraryAndAddsThePlaylistTables() {
        helper.createDatabase(db, 2).use { v2 ->
            v2.execSQL("INSERT INTO artist (id, name, sort_name) VALUES (7, 'Rui Veloso', 'rui veloso')")
        }

        helper.runMigrationsAndValidate(db, 3, true, Migrations.MIGRATION_2_3).use { v3 ->
            v3.query("SELECT name FROM artist WHERE id = 7").use {
                assertTrue(it.moveToFirst())
                assertEquals("Rui Veloso", it.getString(0))
            }
            v3.execSQL(
                "INSERT INTO playlist (id, name, created_at, updated_at, last_used_at) VALUES (1, 'Gym', 1, 1, 1)",
            )
            v3.execSQL("INSERT INTO playlist_song (playlist_id, song_id, position, added_at) VALUES (1, 42, 0, 1)")
            assertEquals(setOf("playlist"), foreignKeys(v3, "playlist_song"))
        }
    }

    @Test
    fun migration3To4KeepsPlaylistsAndAddsTheAppendOnlyAddLog() {
        helper.createDatabase(db, 3).use { v3 ->
            v3.execSQL(
                "INSERT INTO playlist (id, name, created_at, updated_at, last_used_at) VALUES (1, 'Gym', 1, 1, 1)",
            )
            v3.execSQL("INSERT INTO playlist_song (playlist_id, song_id, position, added_at) VALUES (1, 42, 0, 1)")
        }

        helper.runMigrationsAndValidate(db, 4, true, Migrations.MIGRATION_3_4).use { v4 ->
            v4.query("SELECT COUNT(*) FROM playlist_song").use {
                assertTrue(it.moveToFirst())
                assertEquals(1, it.getInt(0))
            }
            v4.execSQL("INSERT INTO playlist_add_event (song_id, playlist_id, at) VALUES (42, 1, 5)")
            assertEquals(setOf<String>(), foreignKeys(v4, "playlist_add_event"))
        }
    }

    @Test
    fun migration4To5KeepsPlaylistsAndAddsTheDismissedTableThatCascades() {
        helper.createDatabase(db, 4).use { v4 ->
            v4.execSQL(
                "INSERT INTO playlist (id, name, created_at, updated_at, last_used_at) VALUES (1, 'Gym', 1, 1, 1)",
            )
            v4.execSQL("INSERT INTO playlist_song (playlist_id, song_id, position, added_at) VALUES (1, 42, 0, 1)")
            v4.execSQL("INSERT INTO playlist_add_event (song_id, playlist_id, at) VALUES (42, 1, 5)")
        }

        helper.runMigrationsAndValidate(db, 5, true, Migrations.MIGRATION_4_5).use { v5 ->
            v5.query("SELECT COUNT(*) FROM playlist_song").use {
                assertTrue(it.moveToFirst())
                assertEquals(1, it.getInt(0))
            }
            v5.query("SELECT COUNT(*) FROM playlist_add_event").use {
                assertTrue(it.moveToFirst())
                assertEquals(1, it.getInt(0))
            }
            v5.execSQL("PRAGMA foreign_keys = ON")
            v5.execSQL("INSERT INTO playlist_dismissed (playlist_id, song_id, at) VALUES (1, 77, 9)")
            assertEquals(setOf("playlist"), foreignKeys(v5, "playlist_dismissed"))

            v5.execSQL("DELETE FROM playlist WHERE id = 1")
            v5.query("SELECT COUNT(*) FROM playlist_dismissed").use {
                assertTrue(it.moveToFirst())
                assertEquals(0, it.getInt(0))
            }
        }
    }

    @Test
    fun migration5To6KeepsTheLibraryAndAddsTheSoundProfileTables() {
        helper.createDatabase(db, 5).use { v5 ->
            v5.execSQL("INSERT INTO artist (id, name, sort_name) VALUES (7, 'Rui Veloso', 'rui veloso')")
        }

        helper.runMigrationsAndValidate(db, 6, true, Migrations.MIGRATION_5_6).use { v6 ->
            v6.query("SELECT name FROM artist WHERE id = 7").use {
                assertTrue(it.moveToFirst())
                assertEquals("Rui Veloso", it.getString(0))
            }
            v6.execSQL(
                "INSERT INTO sound_profile (song_id, profiler_version, features, source, file_size, analysed_at) " +
                    "VALUES (41, 1, X'0000803F', 'mac', 100, 5)",
            )
            v6.execSQL(
                "INSERT INTO analysis_failure (song_id, profiler_version, attempts, last_error) " +
                    "VALUES (41, 1, 2, 'decode failed')",
            )
            v6.query("SELECT length(features), source FROM sound_profile WHERE song_id = 41").use {
                assertTrue(it.moveToFirst())
                assertEquals(4, it.getInt(0))
                assertEquals("mac", it.getString(1))
            }
            // Keyed by song id with no foreign key to song (ADR 0058, as 0037).
            assertEquals(setOf<String>(), foreignKeys(v6, "sound_profile"))
            assertEquals(setOf<String>(), foreignKeys(v6, "analysis_failure"))
        }
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
