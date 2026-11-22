package dev.tomasflopes.musicplayer.core.model.stats

/**
 * The listened-time rule behind "minutes listened" (ADR 0090). Rows written by phase 1 builds have unreliable
 * `listened_ms` and `song_duration_ms` (ADR 0025), so a row counts only when both values are possible, and never
 * more than the song lasts: the total can be too small, but never too large.
 */
object ListenedTime {
    /** No MP3 in the library lasts longer; a larger duration is a bad value, not a long song. */
    const val MAX_SONG_MS: Long = 4L * 60 * 60 * 1000

    /** What one row adds: 0 for a missing or impossible value, else the listened time up to the duration. */
    fun countedMs(
        listenedMs: Long,
        songDurationMs: Long,
    ): Long =
        if (listenedMs <= 0 || songDurationMs <= 0 || songDurationMs > MAX_SONG_MS) {
            0L
        } else {
            minOf(listenedMs, songDurationMs)
        }
}
