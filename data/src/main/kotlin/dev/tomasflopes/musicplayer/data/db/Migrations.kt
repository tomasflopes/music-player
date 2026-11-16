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

    /** Taste signals (ADR 0049): the append-only log of songs added to playlists. One table and its index. */
    val MIGRATION_3_4: Migration =
        object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `playlist_add_event` " +
                        "(`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`song_id` INTEGER NOT NULL, `playlist_id` INTEGER NOT NULL, `at` INTEGER NOT NULL)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_playlist_add_event_at` ON `playlist_add_event` (`at`)",
                )
            }
        }

    /**
     * Playlist suggestions (ADR 0051): the songs dismissed per playlist. One table, no extra index (the primary key
     * starts with `playlist_id`). It cascades from `playlist` like `playlist_song`.
     */
    val MIGRATION_4_5: Migration =
        object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `playlist_dismissed` (`playlist_id` INTEGER NOT NULL, " +
                        "`song_id` INTEGER NOT NULL, `at` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`playlist_id`, `song_id`), " +
                        "FOREIGN KEY(`playlist_id`) REFERENCES `playlist`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE )",
                )
            }
        }

    /**
     * Sound profiles (ADR 0058): one row per song with its BLOB, and one failure row per song and profiler version.
     * Adds two tables and nothing else; the queue is a query, so there is no queue table.
     */
    val MIGRATION_5_6: Migration =
        object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `sound_profile` (`song_id` INTEGER NOT NULL, " +
                        "`profiler_version` INTEGER NOT NULL, `features` BLOB NOT NULL, `source` TEXT NOT NULL, " +
                        "`file_size` INTEGER NOT NULL, `analysed_at` INTEGER NOT NULL, PRIMARY KEY(`song_id`))",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `analysis_failure` (`song_id` INTEGER NOT NULL, " +
                        "`profiler_version` INTEGER NOT NULL, `attempts` INTEGER NOT NULL, " +
                        "`last_error` TEXT NOT NULL, PRIMARY KEY(`song_id`, `profiler_version`))",
                )
            }
        }

    /**
     * Mixes (ADR 0076): the mix and its seeds. Two tables and an index; no score table (a mix's state is read from the
     * log), no foreign key to `song` or `artist` (as ADR 0037). `play_event.mix_id` has existed since version 1.
     */
    val MIGRATION_6_7: Migration =
        object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `mix` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL)",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `mix_seed` (`mix_id` INTEGER NOT NULL, `position` INTEGER NOT NULL, " +
                        "`kind` TEXT NOT NULL, `ref_id` INTEGER NOT NULL, PRIMARY KEY(`mix_id`, `kind`, `ref_id`), " +
                        "FOREIGN KEY(`mix_id`) REFERENCES `mix`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_mix_seed_mix_id_position` ON `mix_seed` (`mix_id`, `position`)",
                )
            }
        }

    val ALL: Array<Migration> =
        arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
}
