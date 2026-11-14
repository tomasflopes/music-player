package dev.tomasflopes.musicplayer.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Every schema change is a hand-written migration with its own test (CONTRIBUTING.md). Never destructive:
 * the listening log must survive every upgrade.
 */
object Migrations {
    /** Artist photos (ADR 0030): the owner's per-artist choice and the stored Deezer lookups. Adds two tables only. */
    val MIGRATION_1_2: Migration =
        object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `artist_image_override` (`artist_id` INTEGER NOT NULL, " +
                        "`kind` TEXT NOT NULL, `value` TEXT, `updated_at` INTEGER NOT NULL, PRIMARY KEY(`artist_id`))",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `artist_image_lookup` (`artist_id` INTEGER NOT NULL, " +
                        "`picture_url` TEXT, `checked_at` INTEGER NOT NULL, PRIMARY KEY(`artist_id`))",
                )
            }
        }

    /** Playlists (ADR 0037): the playlist and its songs. Adds two tables and their indexes only. */
    val MIGRATION_2_3: Migration =
        object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `playlist` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, " +
                        "`last_used_at` INTEGER NOT NULL)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_playlist_last_used_at` ON `playlist` (`last_used_at`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `playlist_song` (`playlist_id` INTEGER NOT NULL, " +
                        "`song_id` INTEGER NOT NULL, `position` INTEGER NOT NULL, `added_at` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`playlist_id`, `song_id`), " +
                        "FOREIGN KEY(`playlist_id`) REFERENCES `playlist`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE )",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_playlist_song_song_id` ON `playlist_song` (`song_id`)")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_playlist_song_playlist_id_position` " +
                        "ON `playlist_song` (`playlist_id`, `position`)",
                )
            }
        }

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3)
}
