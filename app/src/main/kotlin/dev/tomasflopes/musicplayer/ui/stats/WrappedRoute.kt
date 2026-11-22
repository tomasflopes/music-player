package dev.tomasflopes.musicplayer.ui.stats

import dev.tomasflopes.musicplayer.core.taste.stats.StatsPeriod

/** The Wrapped page of one past month or year, opened from the Stats tab. */
object WrappedRoute {
    const val ARG_KEY = "key"
    const val PATTERN = "wrapped/{$ARG_KEY}"

    fun create(period: StatsPeriod) = "wrapped/${period.key}"
}
