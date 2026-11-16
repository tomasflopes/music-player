package dev.tomasflopes.musicplayer.core.taste.mix

/** JUnit's `assertNotNull` returns Unit in Kotlin; this one returns the value so a test can use it. */
internal fun <T : Any> notNull(value: T?): T = requireNotNull(value) { "expected a value, got null" }
