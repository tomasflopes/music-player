package dev.tomasflopes.musicplayer.data.db

import androidx.room.migration.Migration

/**
 * Every schema change is a hand-written migration with its own test (CONTRIBUTING.md). Never destructive:
 * the listening log must survive every upgrade.
 */
object Migrations {
    val ALL: Array<Migration> = emptyArray()
}
