package dev.tomasflopes.musicplayer.core.analysis

import dev.tomasflopes.musicplayer.core.analysis.config.WindowConfig

/** A stretch of the song to analyse, in song time. */
data class AnalysisWindow(
    val startMs: Long,
    val lengthMs: Long,
)

/**
 * Picks the windows (three, centred at 25 / 50 / 75 % of the song, 20 s each) that skip intros and outros.
 * Short songs shrink the windows to the song and drop duplicates.
 */
class WindowPlanner(
    private val config: WindowConfig = WindowConfig(),
) {
    fun plan(durationMs: Long): List<AnalysisWindow> {
        if (durationMs <= 0L) return emptyList()
        val length = minOf(config.lengthMs, durationMs)
        val windows =
            config.centres.map { centre ->
                val start = (durationMs * centre).toLong() - length / 2
                AnalysisWindow(start.coerceIn(0L, durationMs - length), length)
            }
        return windows.distinct()
    }
}
