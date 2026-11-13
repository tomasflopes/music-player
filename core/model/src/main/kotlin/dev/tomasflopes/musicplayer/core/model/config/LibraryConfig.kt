package dev.tomasflopes.musicplayer.core.model.config

/** Every tunable number about the library, in one place. */
data class LibraryConfig(
    val recentlyAddedDefaultDays: Int = 20,
    val recentlyAddedQuickOptions: List<Int> = listOf(7, 15, 20, 30, 60, 90),
    val recentlyAddedDaysRange: IntRange = 1..3650,
    val scanBatchSize: Int = 500,
    /** How long MediaStore must stay quiet before a change triggers a rescan. */
    val mediaStoreDebounceMs: Long = 5_000,
)
