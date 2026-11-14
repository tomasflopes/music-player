package dev.tomasflopes.musicplayer.ui.search

import javax.inject.Qualifier

/** The dispatcher the Search tab's library flows run on, so a test can control it. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class SearchWorkDispatcher
