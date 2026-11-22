package dev.tomasflopes.musicplayer.ui.stats

import dev.tomasflopes.musicplayer.core.taste.stats.StatsPeriod
import java.time.format.TextStyle
import java.util.Locale

/** "November 2026" for a month, "2026" for a year. */
fun periodLabel(
    period: StatsPeriod,
    locale: Locale = Locale.getDefault(),
): String =
    when (period) {
        is StatsPeriod.Month -> "${period.value.month.getDisplayName(
            TextStyle.FULL_STANDALONE,
            locale,
        )} ${period.value.year}"
        is StatsPeriod.Year -> period.value.toString()
    }
