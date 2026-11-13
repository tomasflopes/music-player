package dev.tomasflopes.musicplayer.ui.nowplaying

private const val MS_PER_SECOND = 1_000L
private const val SECONDS_PER_MINUTE = 60L
private const val SECONDS_PER_HOUR = 3_600L

/** `m:ss`, or `h:mm:ss` from an hour up; a partial second rounds down and a negative time shows zero. */
internal fun formatPlaybackTime(ms: Long): String {
    val totalSeconds = ms.coerceAtLeast(0) / MS_PER_SECOND
    val hours = totalSeconds / SECONDS_PER_HOUR
    val minutes = totalSeconds % SECONDS_PER_HOUR / SECONDS_PER_MINUTE
    val seconds = totalSeconds % SECONDS_PER_MINUTE
    return if (hours > 0) "$hours:${minutes.twoDigits()}:${seconds.twoDigits()}" else "$minutes:${seconds.twoDigits()}"
}

private fun Long.twoDigits(): String = toString().padStart(2, '0')
