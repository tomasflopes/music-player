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

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2)
}
