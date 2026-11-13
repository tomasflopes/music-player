package dev.tomasflopes.musicplayer.ui

import kotlinx.coroutines.flow.SharingStarted

/** How long a screen's flows keep running after its last subscriber leaves (rotation must not restart them). */
internal val WhileSubscribed: SharingStarted = SharingStarted.WhileSubscribed(5_000L)
